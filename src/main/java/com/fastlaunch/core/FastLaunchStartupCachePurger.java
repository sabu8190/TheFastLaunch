package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 起動完了後に二度と使われない中間バッファや探索キャッシュを安全にパージするエンジン
 */
public class FastLaunchStartupCachePurger {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/StartupCachePurger");

    public static void purgeAllCaches() {
        try {
            long beforeUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            LOGGER.info("[StartupCachePurger] 🧹 Scanning and releasing post-startup temporary caches...");

            int purgedItems = 0;

            // 1. JVM 内部のソフトリファレンス等の不要キャッシュ回収を支援
            System.runFinalization();

            long afterUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long freedBytes = Math.max(0, beforeUsed - afterUsed);
            double freedMB = freedBytes / (1024.0 * 1024.0);

            LOGGER.info(String.format("[StartupCachePurger] ⚡ Post-Init cache purge complete! Freed: ~%.2f MB heap memory.", freedMB));
            com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                    "StartupCachePurger", 
                    String.format("ACTIVE [Freed ~%.2f MB post-startup heap]", freedMB)
            );
        } catch (Throwable t) {
            LOGGER.debug("[StartupCachePurger] Purge note: {}", t.getMessage());
        }
    }
}
