package com.fastlaunch.core;

import com.fastlaunch.config.FastLaunchConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 3段階適応型メモリガバナー (Adaptive Memory Governor)
 * リアルタイムの JVM ヒープ使用率 (used / max) に応じて動的にキャッシュ解放・GC 介入を行う。
 */
public class FastLaunchAdaptiveMemoryGovernor {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/AdaptiveGovernor");
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FastLaunch-AdaptiveGovernorThread");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static volatile long lastPurgeTime = 0;

    public static void start() {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        SCHEDULER.scheduleWithFixedDelay(() -> {
            try {
                double warnThreshold = FastLaunchConfig.MEMORY_PURGE_THRESHOLD_PERCENT;
                double criticalThreshold = FastLaunchConfig.CRITICAL_PURGE_THRESHOLD_PERCENT;

                Runtime rt = Runtime.getRuntime();
                long maxMem = rt.maxMemory();
                long totalMem = rt.totalMemory();
                long freeMem = rt.freeMemory();
                long usedMem = totalMem - freeMem;

                double usedPercent = ((double) usedMem / maxMem) * 100.0;
                long now = System.currentTimeMillis();

                if (usedPercent >= criticalThreshold) {
                    // Level 3: 緊急状態 (Critical)
                    if (now - lastPurgeTime > 30000) { // 30秒間隔
                        lastPurgeTime = now;
                        LOGGER.warn("[AdaptiveGovernor] 🚨 Level 3 Critical Memory Pressure: {:.1f}% used (Threshold: {:.1f}%). Triggering Emergency Eviction & Compaction!", usedPercent, criticalThreshold);
                        FastLaunchStartupCachePurger.purgeAllCaches();
                        System.gc();
                    }
                } else if (usedPercent >= warnThreshold) {
                    // Level 2: 警告状態 (Warning)
                    if (now - lastPurgeTime > 60000) { // 60秒間隔
                        lastPurgeTime = now;
                        LOGGER.info("[AdaptiveGovernor] 🧹 Level 2 High Memory Pressure: {:.1f}% used (Threshold: {:.1f}%). Reclaiming unused startup buffers...", usedPercent, warnThreshold);
                        if (FastLaunchConfig.ENABLE_STARTUP_CACHE_PURGE) {
                            FastLaunchStartupCachePurger.purgeAllCaches();
                        }
                    }
                }
                // Level 1: 正常運転 (< 80%) -> GC を一切トリガーせずフレームレートを最大維持
            } catch (Throwable t) {
                LOGGER.debug("[AdaptiveGovernor] Monitoring error: {}", t.getMessage());
            }
        }, 10, 15, TimeUnit.SECONDS);

        LOGGER.info("[AdaptiveGovernor] 🚀 3-Tier Adaptive Memory Governor active (Monitoring heap used/max)!");
    }
}
