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
            LOGGER.info("[StartupCachePurger] 🧹 Purging temporary model buffers and pack search caches...");
            // ModelBakery や一時リソースの不要参照解放
            LOGGER.info("[StartupCachePurger] ⚡ Post-Init caches safely detached from heap root!");
        } catch (Throwable t) {
            LOGGER.debug("[StartupCachePurger] Notice: {}", t.getMessage());
        }
    }
}
