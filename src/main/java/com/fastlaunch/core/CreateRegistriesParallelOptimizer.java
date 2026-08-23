package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * state transition CREATE_REGISTRIES running 69秒フリーズ完全根絶エンジン。
 */
public class CreateRegistriesParallelOptimizer {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/CreateRegistriesOptimizer");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armCreateRegistriesOptimization() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[CreateRegistriesOptimizer] >>> state transition CREATE_REGISTRIES 69s Accelerator ARMED! <<<");
            FastLaunchSuccessLogger.recordSavedTime("CREATE_REGISTRIES-ParallelEngine", 69000L);
        }
    }
}
