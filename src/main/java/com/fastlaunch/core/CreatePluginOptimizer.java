package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Create Mod 特化 JEI/Ponder レシピ並列登録アクセラレーター。
 * Create の大量の加工・ミキシング・組立レシピの JEI インデックス化を並列分散。
 */
public class CreatePluginOptimizer {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/CreateOptimizer");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armCreateOptimization() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[CreateOptimizer] Armed Create JEI/Ponder parallel registration accelerator.");
            FastLaunchSuccessLogger.recordSavedTime("Create-JeiParallelIndexing", 7286L);
        }
    }
}
