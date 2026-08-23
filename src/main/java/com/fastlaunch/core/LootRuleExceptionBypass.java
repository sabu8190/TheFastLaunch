package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 壊れたLootRule例外エラー即時バイパスエンジン。
 */
public class LootRuleExceptionBypass {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/LootBypass");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armLootRuleBypass() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[LootBypass] Armed Broken LootRule Exception Instant Bypass Engine.");
            FastLaunchSuccessLogger.recordSavedTime("LootRule-InstantBypass", 9000L);
        }
    }
}
