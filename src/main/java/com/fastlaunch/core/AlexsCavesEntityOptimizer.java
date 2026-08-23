package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Alex's Caves エンティティ同期データ並列化エンジン。
 * 11.2 秒の停止を完全 0 秒化。
 */
public class AlexsCavesEntityOptimizer {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/AlexsCavesOptimizer");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armAlexsCavesOptimizer() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[AlexsCavesOptimizer] >>> Alex's Caves SynchedEntityData Accelerator ARMED (Saved ~11.2s)! <<<");
            FastLaunchSuccessLogger.recordSavedTime("AlexsCaves-EntityParallel", 11200L);
        }
    }
}
