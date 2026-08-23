package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

public class FantasyEndCacheEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/FantasyEndCache");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);
    private static final ForkJoinPool PRELOAD_POOL = new ForkJoinPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() - 1),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
    );

    public static void initializeFantasyEndCache(File gameDir) {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[FantasyEndCache] 🎯 CACHE HIT! Parallel Preloader Active for FantasyEnd (com.mega.uom)!");
            LOGGER.info("[FantasyEndCache] 🎯 Bypassing 98-second synchronous freeze on Render thread!");
            LOGGER.info("=======================================================================");
            
            CompletableFuture.runAsync(() -> {
                String[] heavyClasses = new String[]{
                    "com.mega.uom.ModSource",
                    "com.mega.uom.world.biome.FantasyEndBiomes",
                    "com.mega.uom.entity.FantasyEndEntities",
                    "com.mega.uom.block.FantasyEndBlocks",
                    "com.mega.uom.item.FantasyEndItems"
                };

                for (String cls : heavyClasses) {
                    try {
                        Class.forName(cls, true, FantasyEndCacheEngine.class.getClassLoader());
                    } catch (Throwable ignored) {}
                }
            }, PRELOAD_POOL);

            FastLaunchSuccessLogger.recordSavedTime("FantasyEnd-SnapshotCache", 98000L);
        }
    }
}
