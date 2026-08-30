package com.fastlaunch.core;

import com.fastlaunch.config.FastLaunchConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spark仕様書準拠: 割り当てメモリ使用率 (used/max) に基づく多段階適応型メモリガバナー
 */
public class FastLaunchAdaptiveMemoryGovernor {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/AdaptiveMemoryGovernor");
    private static final AtomicBoolean LOAD_COMPLETE_PURGED = new AtomicBoolean(false);

    public static void evaluateAndPurgeAsync(String phase) {
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            try {
                Runtime rt = Runtime.getRuntime();
                long maxMemory = rt.maxMemory();
                long totalMemory = rt.totalMemory();
                long freeMemory = rt.freeMemory();
                long usedMemory = totalMemory - freeMemory;

                double usageRatio = (double) usedMemory / (double) maxMemory;
                double usagePercent = usageRatio * 100.0;

                FastLaunchConfig cfg = FastLaunchConfig.get();
                double warnThreshold = cfg.memory_purge_threshold_percent;
                double criticalThreshold = cfg.critical_purge_threshold_percent;

                LOGGER.info("=======================================================================");
                LOGGER.info("[AdaptiveGovernor] 📊 Memory Evaluation at phase: {}", phase);
                LOGGER.info("[AdaptiveGovernor] 📊 Heap Used: {} MB / Max: {} MB ({:.1f}%)",
                        usedMemory / (1024 * 1024), maxMemory / (1024 * 1024), usagePercent);

                if (usagePercent >= criticalThreshold) {
                    LOGGER.info("[AdaptiveGovernor] 🚨 Level 3 (Critical >= {:.1f}%): Force Deep Purge & Soft-Ref Eviction!", criticalThreshold);
                    FastLaunchStartupCachePurger.purgeAllCaches();
                    System.gc();
                } else if (usagePercent >= warnThreshold) {
                    LOGGER.info("[AdaptiveGovernor] ⚠️ Level 2 (Warning >= {:.1f}%): Reclaiming startup caches & model buffers...", warnThreshold);
                    FastLaunchStartupCachePurger.purgeAllCaches();
                    System.gc();
                } else {
                    LOGGER.info("[AdaptiveGovernor] ✅ Level 1 (Normal < {:.1f}%): Memory headroom healthy. Redundant GC suppressed.", warnThreshold);
                    if (cfg.enable_startup_cache_purge) {
                        FastLaunchStartupCachePurger.purgeAllCaches();
                    }
                }

                long afterUsed = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                long saved = (usedMemory / (1024 * 1024)) - afterUsed;
                if (saved > 0) {
                    LOGGER.info("[AdaptiveGovernor] ⚡ Successfully RECLAIMED {} MB of RAM! (Current Used: {} MB)", saved, afterUsed);
                }
                LOGGER.info("=======================================================================");
            } catch (Throwable t) {
                LOGGER.debug("[AdaptiveGovernor] Evaluation notice: {}", t.getMessage());
            }
        });
    }
}
