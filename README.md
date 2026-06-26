# RiftAutoTune

Client-side Forge **1.20.1** mod for the *Nightfall Chronicles* dark-fantasy modpack. It
benchmarks your hardware on first launch and auto-configures **vanilla graphics + Oculus shaders +
Distant Horizons** to hold a target **FPS band (default 60–100)**, keeping visuals as high as the
hardware allows. It then keeps you in band during play. No manual tuning.

> Java 17 · Forge 47.x · client-only · every integrated mod is an **optional** dependency.

---

## Build

```bash
# from this folder
./gradlew build          # jar in build/libs/
./gradlew test           # runs the pure-logic unit tests (no Minecraft needed)
./gradlew runClient      # launch a dev client
```

**First-time setup:** the wrapper scripts (`gradlew`, `gradlew.bat`) and
`gradle/wrapper/gradle-wrapper.properties` are included, but the binary
`gradle/wrapper/gradle-wrapper.jar` is not. Get it one of two ways:

- **IDE import (easiest):** open this folder in IntelliJ IDEA (or Eclipse + Buildship). The IDE
  reads `gradle-wrapper.properties`, provisions Gradle 8.8, and runs the build/import for you.
- **CLI:** with a local Gradle installed, run `gradle wrapper --gradle-version 8.8` once to generate
  the jar, then `./gradlew build` works. (Or just run `gradle build` directly.)

Requires a JDK 17. The first build downloads Forge/Minecraft/mappings from
`maven.minecraftforge.net` and `libraries.minecraft.net`, so it needs internet access. The `core` package has **no Minecraft imports**, so its tests run as plain
JUnit. The optional **Distant Horizons API** can be wired in via the commented `compileOnly`
dependency in `build.gradle`; without it the DH integration uses runtime reflection.

---

## How it works

```
LoggingIn ─▶ HardwareDetector (render thread: GL/GLFW/JMX)
          └▶ fingerprint match? ── yes ─▶ restore saved profile ─▶ apply
                                 └─ no  ─▶ apply tier preset
                                          ─▶ BenchmarkHarness (steady-state + yaw sweep)
                                          ─▶ AutoTuneOptimizer (async)
                                          ─▶ apply via adapters (render thread) + save
          └▶ AdaptiveController keeps FPS in band during play
```

### 1. Detection (`client/HardwareDetector`)
GPU vendor/renderer/version and VRAM (`NVX_gpu_memory_info` / `ATI_meminfo`), CPU threads, system
RAM, `-Xmx`, resolution, refresh rate, GUI scale. **All GL/GLFW calls run on the render thread**
because the GL context is thread-bound. Produces a `HardwareProfile` with a tier (LOW/MEDIUM/HIGH/
ULTRA) and a stable fingerprint.

### 2. Benchmark (`client/BenchmarkHarness`)
Runs once in-world. **Excludes warm-up** (chunk loading + DH LOD generation) by waiting for a
steady frame-time variance, then samples over a fixed **yaw sweep** for reproducibility. Computes
avg, 1% low, 0.1% low and a stability score (`core/BenchmarkResult`).

### 3. Optimize (`core/AutoTuneOptimizer` + `core/CostModel`)
A cost model anchored on the measured average predicts FPS for any settings set. A greedy/hill-climb
search **downgrades** the cheapest-visual / highest-cost knobs first (shadow-map resolution, DH CPU
load, volumetric fog) to reach the band and the 1%-low floor, then **upgrades** to spend headroom up
to a quality "aim" controlled by `qualityBias`. The band is **display-aware**: clamped to the
refresh rate, with VSync treated as a hard cap.

### 4. Apply (`adapter/*`)
Resolved settings are written through four adapters on the render thread; config files are backed up
first (`*.riftbak`).

### 5. Adaptive loop (`client/AdaptiveController`)
Rolling FPS monitor with hysteresis, cooldown and a per-minute change cap. Steps one rung at a time,
ignores transient combat/particle dips, and **pauses while any screen is open** (so it never fights
manual edits).

---

## Oculus ↔ Distant Horizons integration (read this)

This integration is **genuinely fragile on 1.20.1** and depends on exact versions (Oculus + DH 2.x
have well-documented LOD-lighting/ClassNotFound issues). RiftAutoTune therefore **does not assume it
works** — it detects, adapts, and degrades:

- If the active shaderpack advertises DH support → align DH LOD distance with the vanilla
  render-distance handoff and let the shader own fog/lighting.
- If not (common with older Oculus) → request DH **"Old Lighting"** so LODs still get shaded instead
  of rendering broken/black, or render DH without shader effects.
- Shaderpack handling is **pack-agnostic**: it tunes whatever pack is installed/active, with a
  license-clean, DH-compatible default of **Complementary Reimagined**. No specific/delisted
  derivative pack is hard-required.

---

## Exact config surfaces touched

| File | Format | Written by |
|------|--------|-----------|
| `options.txt` | vanilla | `VanillaConfigAdapter` (via `Options` API) |
| `config/embeddium-options.json` | **JSON** | `EmbeddiumAdapter` (safe perf flags only) |
| `config/oculus.properties` | properties | `OculusAdapter` (`enableShaders`, `shaderPack`) |
| `shaderpacks/<pack>.txt` | properties | `OculusAdapter` (option overrides) |
| Distant Horizons config | via `DhApi` (reflection) | `DistantHorizonsAdapter` |
| `config/riftautotune-client.toml` | TOML | this mod (settings) |
| `config/riftautotune-profile.json` | JSON | this mod (saved profile + fingerprint) |

Originals are copied to `<file>.riftbak` before the first overwrite.

---

## ⚠️ TODO: external API signatures to confirm against installed versions

These are isolated behind adapters + reflection and are the only places that touch private/unstable
surfaces. Confirm each against your installed mod versions and replace the reflective fallback:

- **`OculusAdapter`** — `oculus.properties` keys (`enableShaders`, `shaderPack`); the
  `shaderpacks/<pack>.txt` option keys (only the near-universal `shadowMapResolution` is written by
  default; pack-specific keys are commented out); the reload entry point
  (`net.irisshaders.iris.api.v0.IrisApi#reload` or `net.coderbot.iris.Iris#reload`).
- **`EmbeddiumAdapter`** — the `advanced` JSON key names for the performance flags.
- **`DistantHorizonsAdapter`** — the `DhApi.Delayed.configs` getter/setter chain
  (`lodChunkRenderDistanceRadius`, `cpuLoad`, `verticalQuality`, `lodDetail`), the "Old Lighting"
  config path, and reliable DH-aware-shader detection.
- **`BenchmarkHarness`** — optional multi-tuple sweep to calibrate per-knob costs on the exact
  machine, and a render-distance-reduced pass for a real GPU/CPU-bound classification (the optimizer
  works without these via tier-scaled default costs).

---

## Commands & config

```
/riftautotune benchmark              # re-run the benchmark now
/riftautotune apply                  # re-apply the current resolved settings
/riftautotune status                 # tier, band, last benchmark, availability, mode
/riftautotune reset                  # clear saved profile (re-benchmark next join)
/riftautotune profile <low|medium|high|ultra>   # force a tier preset
```

`config/riftautotune-client.toml`: `targetFpsMin` (60), `targetFpsMax` (100, auto-clamped to your
refresh rate), `onePctLowFloor` (50), `enableAdaptiveMode` (true), `benchmarkSeconds` (8),
`qualityBias` (0.5), `preferredShaderpack` (`ComplementaryReimagined`), `debugLogging` (false).

---

## Verifying the FPS-band result

1. Launch, join a world, watch the toast: *Benchmarking… → Applied <tier> preset (<fps>)*.
2. Open the F3 graph (or any FPS meter). After tuning, the average should sit inside your configured
   band; clamped to your monitor (e.g. a 60 Hz panel targets ~55–60).
3. `/riftautotune status` prints the measured avg / 1% / 0.1% and the applied tier.
4. Move into a heavy scene; the adaptive loop should step quality down within a few seconds if FPS
   falls below the floor (debug logging shows each adjustment).

---

## Architecture

```
core/        Pure Java (no Minecraft). Optimizer, cost model, tiers, percentiles. Unit-tested.
client/      Render-thread glue: detection, benchmark, adaptive loop, executor, persistence, HUD.
adapter/     Defensive reflection writers for vanilla / Embeddium / Oculus / Distant Horizons.
command/     /riftautotune client commands.
util/        Logging, mod-presence checks, reflection helpers.
```

The `core` package is deliberately Minecraft-free so the tuning logic is verifiable without the
game; `src/test` covers the optimizer holding the band, feature gating, tier classification and the
percentile math.

## License
MIT.
