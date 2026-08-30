package com.fastlaunch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * TheFastLaunch v-b1.6 設定管理システム (config/fastlaunch.json)
 */
public class FastLaunchConfig {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/fastlaunch.json");
    private static FastLaunchConfig INSTANCE = new FastLaunchConfig();

    public double memory_purge_threshold_percent = 80.0;
    public double critical_purge_threshold_percent = 92.0;
    public boolean enable_startup_cache_purge = true;
    public boolean enable_create_registries_parallel = true;
    public int max_parallel_threads = 0; // 0 = Auto-detect all cores

    public static FastLaunchConfig get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    FastLaunchConfig loaded = GSON.fromJson(reader, FastLaunchConfig.class);
                    if (loaded != null) {
                        INSTANCE = loaded;
                        LOGGER.info("[Config] 📄 Successfully loaded config/fastlaunch.json (Threshold: {}%)", INSTANCE.memory_purge_threshold_percent);
                        return;
                    }
                }
            }
            // 新規作成
            save();
            LOGGER.info("[Config] 📄 Generated default config/fastlaunch.json");
        } catch (Throwable t) {
            LOGGER.error("[Config] Failed to load config/fastlaunch.json, using defaults: {}", t.getMessage());
        }
    }

    public static void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Throwable t) {
            LOGGER.error("[Config] Failed to save config/fastlaunch.json: {}", t.getMessage());
        }
    }
}
