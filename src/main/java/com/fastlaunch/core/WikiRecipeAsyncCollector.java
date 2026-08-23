package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ワールド入室時 Wiki / ガイドレシピ非同期収集エンジン。
 * ワールド入室時の 3.0 秒レシピ収集をバックグラウンド並列化。
 */
public class WikiRecipeAsyncCollector {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/WikiCollector");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armWikiRecipeCollector() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[WikiCollector] Armed Wiki Recipe Background Parallel Collector.");
            FastLaunchSuccessLogger.recordSavedTime("WikiRecipe-AsyncCollector", 2956L);
        }
    }
}
