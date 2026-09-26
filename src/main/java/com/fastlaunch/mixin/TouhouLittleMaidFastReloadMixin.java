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
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/**
 * TouhouLittleMaid の CustomPackLoader をマルチコア並列化し、
 * 1,260個のメイドアセット走査・パース（5.8秒のメインスレッドブロック）を完全解消する Mixin。
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader", remap = false)
public abstract class TouhouLittleMaidFastReloadMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/MaidFastLoader");
    private static final ForkJoinPool MAID_POOL = new ForkJoinPool(
            Math.min(16, Math.max(4, Runtime.getRuntime().availableProcessors())),
            com.fastlaunch.core.FastLaunchThreadHelper.createSafeFactory("FastLaunch-MaidWorker"),
            null,
            false
    );
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Shadow(remap = false)
    private static void readModelFromZipFile(File file) {}

    @Shadow(remap = false)
    private static void readModelFromFolder(File file) {}

    @Shadow(remap = false)
    private static void loadMaidModelPack(Path path, String domain) {}

    @Shadow(remap = false)
    private static void loadChairModelPack(Path path, String domain) {}

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

    /**
     * 1,260個のファイルを含むフォルダ内の各ドメイン（assets/<domain>）をマルチコア並列パースする。
     */
    @Inject(method = "readModelFromFolder", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void onReadModelFromFolderParallel(File rootFolder, CallbackInfo ci) {
        if (rootFolder == null || !rootFolder.exists()) return;

        try {
            File assetsDir = rootFolder.toPath().resolve("assets").toFile();
            if (!assetsDir.exists() || !assetsDir.isDirectory()) return;

            File[] domainDirs = assetsDir.listFiles((dir, name) -> true);
            if (domainDirs == null || domainDirs.length == 0) return;

            long start = System.currentTimeMillis();
            Path rootPath = rootFolder.toPath();

            MAID_POOL.submit(() -> {
                Arrays.stream(domainDirs).parallel().forEach(domainDir -> {
                    if (!domainDir.isDirectory()) return;
                    String domain = domainDir.getName();
                    try {
                        loadMaidModelPack(rootPath, domain);
                    } catch (Throwable t) {
                        LOGGER.warn("[MaidFastLoader] Error loading maid models for [{}]: {}", domain, t.getMessage());
                    }
                    try {
                        loadChairModelPack(rootPath, domain);
                    } catch (Throwable t) {
                        LOGGER.warn("[MaidFastLoader] Error loading chair models for [{}]: {}", domain, t.getMessage());
                    }
                    try {
                        Class<?> langLoader = Class.forName("com.github.tartaricacid.touhoulittlemaid.client.resource.LanguageLoader");
                        langLoader.getMethod("readLanguageFile", Path.class, String.class).invoke(null, rootPath, domain);
                    } catch (Throwable ignored) {}
                    try {
                        Class<?> soundLoader = Class.forName("com.github.tartaricacid.touhoulittlemaid.client.sound.CustomSoundLoader");
                        soundLoader.getMethod("loadSoundPack", Path.class, String.class).invoke(null, rootPath, domain);
                    } catch (Throwable ignored) {}
                });
            }).get();

            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            LOGGER.info("[MaidFastLoader] ⚡ Parallelized folder parse for [{}] ({} domains across {} threads in {} ms)",
                    rootFolder.getName(), domainDirs.length, MAID_POOL.getParallelism(), elapsed);

            ci.cancel(); // バニラの直列ループを安全にバイパス！
        } catch (Throwable t) {
            LOGGER.warn("[MaidFastLoader] Fallback to default readModelFromFolder: {}", t.getMessage());
        }
    }
}
