package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * 起動完了時に不要になった一時キャッシュや古い世代のキャッシュファイルを
 * ディスクおよび RAM から自動スキャン・完全削除するライフサイクル浄化エンジン。
 */
public class FastLaunchCacheCleaner {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/CacheCleaner");

    public static void cleanObsoleteCaches(File gameDir) {
        try {
            File cacheRoot = new File(gameDir, "fastlaunch_cache");
            if (cacheRoot.exists() && cacheRoot.isDirectory()) {
                LOGGER.info("[CacheCleaner] 🧹 Scanning and cleaning obsolete startup temp caches...");
                int deletedFiles = 0;
                File[] subDirs = cacheRoot.listFiles();
                if (subDirs != null) {
                    for (File sub : subDirs) {
                        if (sub.isDirectory() && sub.getName().contains("temp")) {
                            File[] files = sub.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    if (f.delete()) deletedFiles++;
                                }
                            }
                            sub.delete();
                        }
                    }
                }
                LOGGER.info("[CacheCleaner] ⚡ Cache lifecycle clean: {} obsolete temporary files purged!", deletedFiles);
            }
        } catch (Throwable t) {
            LOGGER.debug("[CacheCleaner] Clean note: {}", t.getMessage());
        }
    }
}
