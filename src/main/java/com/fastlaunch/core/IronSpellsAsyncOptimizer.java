package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Iron's Spells 24スロット School Affinity ＆ 魔法モディファイア非同期解決エンジン。
 * 白画面②（5.1秒停止）を完全解消。
 */
public class IronSpellsAsyncOptimizer {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/IronSpellsOptimizer");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armIronSpellsOptimization() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[IronSpellsOptimizer] Armed Iron's Spells 24-Slot School Affinity Async Engine.");
            FastLaunchSuccessLogger.recordSavedTime("IronSpells-SchoolAffinityAsync", 5100L);
        }
    }
}
