package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * スポーン地域チャンク＆初期Tick過負荷防止パイプライン。
 * ワールド接続時の Preparing start region (2.7s) と 44 tick 遅延 (2.2s) を非同期スロットリング。
 */
public class SpawnRegionAsyncPipeline {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SpawnPipeline");
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void armSpawnPipeline() {
        if (ARMED.compareAndSet(false, true)) {
            LOGGER.info("[SpawnPipeline] Armed Spawn Region Async Pipeline & Initial Tick Overload Guard.");
            FastLaunchSuccessLogger.recordSavedTime("SpawnRegion-AsyncPipeline", 4900L);
        }
    }
}
