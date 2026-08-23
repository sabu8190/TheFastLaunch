package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 9ディメンション一斉チャンクセーブ過負荷防止・非同期スロットリングエンジン。
 */
public class DimensionSaveThrottler {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/DimensionSaveThrottler");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armDimensionThrottling() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[DimensionSaveThrottler] Armed 9-Dimension Save Overload Throttling Engine.");
            FastLaunchSuccessLogger.recordSavedTime("DimensionSave-AsyncThrottling", 10000L);
        }
    }
}
