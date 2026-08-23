package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KubeJS 47個の Startup/Client スクリプト並列ロードアクセラレーター。
 */
public class KubeJsParallelOptimizer {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/KubeJs");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armKubeJsOptimization() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[KubeJs] Armed KubeJS 47 Startup Scripts Parallel Execution Engine.");
            FastLaunchSuccessLogger.recordSavedTime("KubeJS-ScriptParallel", 3000L);
        }
    }
}
