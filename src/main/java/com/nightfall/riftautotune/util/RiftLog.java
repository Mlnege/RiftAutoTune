package com.nightfall.riftautotune.util;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Tiny logging facade with a debug gate so verbose tuning logs can be silenced. */
public final class RiftLog {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean DEBUG = false;

    private RiftLog() {}

    public static void info(String msg, Object... args) {
        LOGGER.info("[RiftAutoTune] " + msg, args);
    }

    public static void warn(String msg, Object... args) {
        LOGGER.warn("[RiftAutoTune] " + msg, args);
    }

    public static void error(String msg, Throwable t) {
        LOGGER.error("[RiftAutoTune] " + msg, t);
    }

    public static void debug(String msg, Object... args) {
        if (DEBUG) LOGGER.info("[RiftAutoTune/debug] " + msg, args);
    }
}
