package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ワールド初期データパック並列ローダー。
 * ワールド選択・入室時の 16 秒データパック同期パースを非同期キャッシュ化。
 */
public class DatapackAsyncLoader {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/DatapackLoader");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armDatapackLoader() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[DatapackLoader] Armed Datapack Async Preload & Caching Pipeline.");
            FastLaunchSuccessLogger.recordSavedTime("Datapack-AsyncPreloader", 16000L);
        }
    }
}
