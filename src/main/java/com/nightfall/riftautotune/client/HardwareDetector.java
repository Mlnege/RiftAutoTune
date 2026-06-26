package com.nightfall.riftautotune.client;

import com.nightfall.riftautotune.core.HardwareProfile;
import com.nightfall.riftautotune.util.RiftLog;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.ATIMeminfo;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.NVXGPUMemoryInfo;

import java.lang.management.ManagementFactory;

/**
 * Reads the hardware/display profile. The GL and GLFW queries MUST run on the render thread
 * (the GL context is bound there) - callers are expected to invoke {@link #detect()} via
 * {@link RiftExecutor#onRenderThread(Runnable)}.
 */
public final class HardwareDetector {

    private HardwareDetector() {}

    /** Must be called on the render thread. */
    public static HardwareProfile detect() {
        Minecraft mc = Minecraft.getInstance();

        String vendor = safeGlString(GL11.GL_VENDOR);
        String renderer = safeGlString(GL11.GL_RENDERER);
        String version = safeGlString(GL11.GL_VERSION);
        int vramMb = detectVramMb();

        int cpuThreads = Runtime.getRuntime().availableProcessors();
        int systemRamMb = detectSystemRamMb();
        int maxHeapMb = (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
        String os = System.getProperty("os.name", "unknown");

        int width = mc.getWindow().getScreenWidth();
        int height = mc.getWindow().getScreenHeight();
        int refresh = detectRefreshRate();
        int guiScale = (int) mc.getWindow().getGuiScale();

        HardwareProfile profile = new HardwareProfile(vendor, renderer, version, vramMb,
                cpuThreads, systemRamMb, maxHeapMb, os, width, height, refresh, guiScale);
        RiftLog.info("Detected {}", profile);
        return profile;
    }

    private static String safeGlString(int name) {
        try {
            String s = GL11.glGetString(name);
            return s == null ? "unknown" : s;
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** VRAM via vendor GL extensions; -1 if unknown (HardwareProfile then uses a heuristic). */
    private static int detectVramMb() {
        try {
            GLCapabilities caps = GL.getCapabilities();
            if (caps != null && caps.GL_NVX_gpu_memory_info) {
                // value is in KiB
                int kb = GL11.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX);
                if (kb > 0) return kb / 1024;
            }
            if (caps != null && caps.GL_ATI_meminfo) {
                // returns 4 ints; first is total free memory in KiB (best available proxy)
                int[] info = new int[4];
                GL11.glGetIntegerv(ATIMeminfo.GL_TEXTURE_FREE_MEMORY_ATI, info);
                if (info[0] > 0) return info[0] / 1024;
            }
        } catch (Throwable t) {
            RiftLog.debug("VRAM detection failed: {}", t.toString());
        }
        return -1;
    }

    @SuppressWarnings("removal")
    private static int detectSystemRamMb() {
        try {
            // com.sun.management exposes total physical memory; guard reflectively for portability.
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                long bytes = sun.getTotalMemorySize();
                return (int) (bytes / (1024 * 1024));
            }
        } catch (Throwable t) {
            RiftLog.debug("System RAM detection failed: {}", t.toString());
        }
        return -1;
    }

    private static int detectRefreshRate() {
        try {
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor != 0L) {
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                if (mode != null && mode.refreshRate() > 0) {
                    return mode.refreshRate();
                }
            }
        } catch (Throwable t) {
            RiftLog.debug("Refresh-rate detection failed: {}", t.toString());
        }
        return 60;
    }
}
