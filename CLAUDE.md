# CLAUDE.md — RiftAutoTune

Project instructions for Claude Code. Read this first.

## What this is
RiftAutoTune is a **client-side Forge 1.20.1 (Java 17)** mod for the *Nightfall Chronicles*
dark-fantasy modpack. It benchmarks hardware on first launch and auto-tunes **vanilla graphics +
Oculus shaders + Distant Horizons** to hold a target **FPS band (default 60–100)**, then keeps the
player in band during play. Every integrated mod is an **optional** dependency; the mod must build
and load even if all of them are absent.

## Build / run
```bash
./gradlew test        # pure-logic unit tests (no Minecraft launch)
./gradlew build       # -> build/libs/riftautotune-1.20.1-1.0.0.jar
./gradlew runClient   # dev client (needs a GPU/display)
```
First-time only: `gradle/wrapper/gradle-wrapper.jar` is **not** committed. Either open the folder in
IntelliJ IDEA (auto-provisions Gradle 8.8 from `gradle-wrapper.properties`), or run
`gradle wrapper --gradle-version 8.8` once. Needs JDK 17 and internet (Forge/MC download on first
build). To use in-game: drop the built jar in the modpack's `mods/`.

## Architecture (keep this shape)
```
core/     Pure Java, NO Minecraft imports. Optimizer, cost model, tiers, percentiles. Unit-tested.
client/   Render-thread glue: detection, benchmark, adaptive loop, executor, persistence, HUD.
adapter/  Defensive reflection writers: Vanilla / Embeddium / Oculus / DistantHorizons.
command/  /riftautotune client commands.
util/     Logging, mod-presence checks, reflection helpers.
```

## Invariants — do not break
- **`core` stays Minecraft-free.** That is what makes the tuning logic unit-testable without the
  game. Put anything touching MC/Forge/LWJGL in `client`/`adapter`, never in `core`.
- **Client-only.** Everything is gated to `Dist.CLIENT`; register nothing on the logical server.
- **GL on the render thread.** All GL/GLFW queries and frame-time sampling run on the render
  thread (the GL context is thread-bound). Heavy work (optimizer, JSON, file I/O) runs on the async
  `RiftExecutor`; results are applied back via `Minecraft.getInstance().execute(...)`.
- **Soft deps via reflection.** Embeddium/Oculus/DH are reached defensively (`ModCompat.loaded(...)`
  then reflection) so a missing mod can never crash the client. Back up any config file before
  overwriting (`*.riftbak`).
- **Pack-agnostic shaders.** Tune whatever Oculus pack is active; default preference is the
  license-clean, DH-compatible **Complementary Reimagined**. Never hard-require a specific/delisted
  derivative pack.
- **English identifiers + comments.**

## ⚠️ TODO — confirm external API signatures against installed versions
These are isolated behind adapters + reflection (`// TODO: confirm signature`). Verify and wire each:
- `adapter/OculusAdapter` — `oculus.properties` keys (`enableShaders`, `shaderPack`);
  `shaderpacks/<pack>.txt` option keys (only `shadowMapResolution` is written by default);
  reload entry point (`net.irisshaders.iris.api.v0.IrisApi#reload` or `net.coderbot.iris.Iris#reload`).
- `adapter/EmbeddiumAdapter` — the `advanced` JSON key names (config/embeddium-options.json is JSON).
- `adapter/DistantHorizonsAdapter` — the `DhApi.Delayed.configs` getter/setter chain
  (`lodChunkRenderDistanceRadius`, `cpuLoad`, `verticalQuality`, `lodDetail`), the "Old Lighting"
  fallback path, and DH-aware-shader detection.
- `client/BenchmarkHarness` — optional multi-tuple sweep to calibrate per-knob costs; a
  render-distance-reduced pass for real GPU/CPU-bound classification.

## Verification status
The optimizer/cost-model/tier/percentile logic is validated (band-holding, feature gating, tier
classification, percentiles). `./gradlew test` runs the JUnit suite in `src/test`. After changing
`core`, keep those tests green. For MC-facing code, sanity-check with `./gradlew runClient`.

## Context
Originated from the verified "RiftAutoTune" Codex prompt (section ④④) in the *Nightfall Chronicles*
Notion page. This modpack is sometimes worked on with other AI tools too, so keep changes
self-contained and well-commented.
