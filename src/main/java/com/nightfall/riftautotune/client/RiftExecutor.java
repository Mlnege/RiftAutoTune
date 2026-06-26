package com.nightfall.riftautotune.client;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Single background thread for the expensive work (optimizer search, JSON, file I/O) plus a
 * helper to bounce results back onto the render thread, where GL state and Minecraft options
 * must be touched.
 */
public final class RiftExecutor {

    private static final ExecutorService ASYNC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RiftAutoTune-Worker");
        t.setDaemon(true);
        return t;
    });

    private RiftExecutor() {}

    /** Run something off the client thread. */
    public static CompletableFuture<Void> async(Runnable task) {
        return CompletableFuture.runAsync(task, ASYNC);
    }

    public static <T> CompletableFuture<T> asyncSupply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, ASYNC);
    }

    /** Run on the render thread (where the GL context is current). */
    public static void onRenderThread(Runnable task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            task.run();
        } else {
            mc.execute(task);
        }
    }

    public static void shutdown() {
        ASYNC.shutdownNow();
    }
}
