package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * ObjectHolder ディレクトリ初期化エンジン。
 */
public class FastLaunchObjectHolderCacheEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ObjectHolderCache");

    public static void initializeObjectHolderCache(File gameDir) {
        try {
            File cacheDir = new File(gameDir, "fastlaunch_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            LOGGER.info("[ObjectHolderCache] 🎯 FastLaunch ObjectHolder directory ready.");
        } catch (Throwable t) {
            LOGGER.debug("[ObjectHolderCache] Notice: {}", t.getMessage());
        }
    }
}
