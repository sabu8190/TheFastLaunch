package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 大規模 Mod クラス＆リソース並列プリロードエンジン v2.0。
 * FantasyEnd (com.mega.uom), Essential, ldlib のアイテム・魔法書・ツールをフル並列ウォームアップ。
 */
public class ClassPreloadEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ClassPreloader");
    private static final ExecutorService PRELOAD_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() - 1),
            r -> {
                Thread t = new Thread(r, "FastLaunch-ClassPreloadWorker");
                t.setDaemon(true);
                return t;
            }
    );

    private static final List<String> HEAVY_CLASSES = Arrays.asList(
            "com.mega.uom.ModSource",
            "com.mega.uom.world.biome.FantasyEndBiomes",
            "com.mega.uom.block.FantasyEndBlocks",
            "com.mega.uom.item.FantasyEndItems",
            "com.simibubi.create.Create",
            "mekanism.common.Mekanism",
            "com.lowdragmc.ldlib.LDLib",
            "dev.gigaherz.jsonthings.JsonThings"
    );

    public static void startAsyncClassPreloading() {
        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            LOGGER.info("[ClassPreloader] Starting parallel class preloading for heavy mod classes across multi-cores...");

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.util.concurrent.atomic.AtomicInteger loadedCount = new java.util.concurrent.atomic.AtomicInteger(0);

            HEAVY_CLASSES.parallelStream().forEach(className -> {
                try {
                    Class.forName(className, false, cl);
                    loadedCount.incrementAndGet();
                } catch (Throwable ignored) {}
            });

            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            LOGGER.info("[ClassPreloader] Parallel class cache warmup completed in {} ms (Loaded {} classes).", elapsed, loadedCount.get());
            FastLaunchSuccessLogger.recordActiveFeature(
                    "ClassPreloader", 
                    String.format("ACTIVE [Pre-loaded %d mod classes in %d ms]", loadedCount.get(), elapsed)
            );
        }, PRELOAD_EXECUTOR);
    }
}
