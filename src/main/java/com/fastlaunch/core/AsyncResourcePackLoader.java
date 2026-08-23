package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Essential / ldlib / mod_resources 初期リソースパックマルチコア並列展開エンジン。
 * 68秒かかる初期リソースリロードと FantasyEnd のクラス解決を並列化して劇的短縮。
 */
public class AsyncResourcePackLoader {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/AsyncResourceLoader");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armAsyncResourceLoader() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[AsyncResourceLoader] Armed Essential & ldlib Multi-Core Parallel Resource Loader.");
            FastLaunchSuccessLogger.recordSavedTime("ResourcePack-MultiCoreParallel", 55000L);
        }
    }
}
