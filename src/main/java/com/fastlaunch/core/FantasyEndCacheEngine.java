package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class FantasyEndCacheEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/FantasyEndCache");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void initializeFantasyEndCache(File gameDir) {
        if (ARMED.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                long start = System.currentTimeMillis();
                String[] heavyClasses = new String[]{
                    "com.mega.uom.ModSource",
                    "com.mega.uom.world.biome.FantasyEndBiomes",
                    "com.mega.uom.entity.FantasyEndEntities",
                    "com.mega.uom.block.FantasyEndBlocks",
                    "com.mega.uom.item.FantasyEndItems"
                };

                int loaded = 0;
                for (String cls : heavyClasses) {
                    try {
                        Class.forName(cls, true, FantasyEndCacheEngine.class.getClassLoader());
                        loaded++;
                    } catch (Throwable ignored) {}
                }
                long elapsed = Math.max(0, System.currentTimeMillis() - start);
                LOGGER.info("[FantasyEndPreloader] Pre-loaded {} FantasyEnd classes in {} ms.", loaded, elapsed);
                FastLaunchSuccessLogger.recordActiveFeature(
                        "FantasyEnd-Preload", 
                        String.format("ACTIVE [Pre-loaded %d classes in %d ms]", loaded, elapsed)
                );
            }, FastLaunchThreadHelper.getSharedWorkerPool());
        }
    }
}
