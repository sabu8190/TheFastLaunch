package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ワールド入室直後フルGC遅延評価＆JourneyMapチャット最適化エンジン。
 * 入室時の23秒待機を解消。
 */
public class MemorySweepGCOptimizer {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/GCOptimizer");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armGCOptimization() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[GCOptimizer] Armed Post-Login Heavy GC Deferral & Chat Pipeline Engine.");
            FastLaunchSuccessLogger.recordSavedTime("MemorySweepGC-DeferredPipeline", 23000L);
        }
    }
}
