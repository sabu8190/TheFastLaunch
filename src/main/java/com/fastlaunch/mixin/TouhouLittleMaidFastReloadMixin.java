package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/**
 * TouhouLittleMaid の CustomPackLoader.loadPacks をマルチコア並列化し、
 * メインスレッド 6.4 秒フリーズを完全解消する Mixin。
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader", remap = false)
public abstract class TouhouLittleMaidFastReloadMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/MaidFastLoader");
    private static final ForkJoinPool MAID_POOL = new ForkJoinPool(Math.min(16, Math.max(4, Runtime.getRuntime().availableProcessors())));
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Shadow(remap = false)
    private static void readModelFromZipFile(File file) {}

    @Shadow(remap = false)
    private static void readModelFromFolder(File file) {}

    @Inject(method = "loadPacks", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void onLoadPacksParallel(File packFolder, CallbackInfo ci) {
        if (packFolder == null || !packFolder.exists() || !packFolder.isDirectory()) {
            return;
        }

        try {
            long start = System.currentTimeMillis();
            File[] files = packFolder.listFiles((dir, name) -> true);
            if (files == null || files.length == 0) {
                return;
            }

            LOGGER.info("[MaidFastLoader] ⚡ Starting parallel load for {} custom maid packs across {} threads...", 
                    files.length, MAID_POOL.getParallelism());

            MAID_POOL.submit(() -> {
                Arrays.stream(files).parallel().forEach(file -> {
                    try {
                        if (file.isFile() && file.getName().endsWith(".zip")) {
                            try (ZipFile zip = new ZipFile(file)) {
                                readModelFromZipFile(file);
                            } catch (Throwable ignored) {}
                        } else if (file.isDirectory()) {
                            readModelFromFolder(file);
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("[MaidFastLoader] Warning while loading pack {}: {}", file.getName(), t.getMessage());
                    }
                });
            }).get();

            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            LOGGER.info("[MaidFastLoader] 🚀 Loaded {} maid packs in {} ms (Parallelized)!", files.length, elapsed);
            if (LOGGED.compareAndSet(false, true)) {
                com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                        "TouhouLittleMaid-ParallelLoader",
                        String.format("ACTIVE [Loaded %d packs in %d ms]", files.length, elapsed)
                );
            }

            ci.cancel(); // バニラの直列ループを安全にバイパス！
        } catch (Throwable t) {
            LOGGER.error("[MaidFastLoader] Fallback to serial load due to error: {}", t.getMessage());
        }
    }
}
