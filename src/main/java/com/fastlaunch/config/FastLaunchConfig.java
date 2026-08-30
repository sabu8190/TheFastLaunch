package com.fastlaunch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * TheFastLaunch 設定管理クラス (config/fastlaunch.json)
 */
public class FastLaunchConfig {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/fastlaunch.json");

    // 設定値
    public static double MEMORY_PURGE_THRESHOLD_PERCENT = 80.0;
    public static double CRITICAL_PURGE_THRESHOLD_PERCENT = 92.0;
    public static boolean ENABLE_STARTUP_CACHE_PURGE = true;
    public static boolean ENABLE_CREATE_REGISTRIES_PARALLEL = true;
    public static int PARALLEL_WORKER_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());

    public static void load() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }

            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        if (json.has("memory_purge_threshold_percent")) {
                            MEMORY_PURGE_THRESHOLD_PERCENT = json.get("memory_purge_threshold_percent").getAsDouble();
                        }
                        if (json.has("critical_purge_threshold_percent")) {
                            CRITICAL_PURGE_THRESHOLD_PERCENT = json.get("critical_purge_threshold_percent").getAsDouble();
                        }
                        if (json.has("enable_startup_cache_purge")) {
                            ENABLE_STARTUP_CACHE_PURGE = json.get("enable_startup_cache_purge").getAsBoolean();
                        }
                        if (json.has("enable_create_registries_parallel")) {
                            ENABLE_CREATE_REGISTRIES_PARALLEL = json.get("enable_create_registries_parallel").getAsBoolean();
                        }
                        if (json.has("parallel_worker_threads")) {
                            PARALLEL_WORKER_THREADS = json.get("parallel_worker_threads").getAsInt();
                        }
                        LOGGER.info("[Config] 📄 Successfully loaded config/fastlaunch.json (Threshold: {}%)", MEMORY_PURGE_THRESHOLD_PERCENT);
                        return;
                    }
                }
            }

            // ファイルが存在しない場合は初期作成
            save();
            LOGGER.info("[Config] 📄 Created default config/fastlaunch.json");
        } catch (Exception e) {
            LOGGER.error("[Config] Failed to load/create config/fastlaunch.json: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            JsonObject json = new JsonObject();
            json.addProperty("memory_purge_threshold_percent", MEMORY_PURGE_THRESHOLD_PERCENT);
            json.addProperty("critical_purge_threshold_percent", CRITICAL_PURGE_THRESHOLD_PERCENT);
            json.addProperty("enable_startup_cache_purge", ENABLE_STARTUP_CACHE_PURGE);
            json.addProperty("enable_create_registries_parallel", ENABLE_CREATE_REGISTRIES_PARALLEL);
            json.addProperty("parallel_worker_threads", PARALLEL_WORKER_THREADS);

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }
            LOGGER.info("[Config] 💾 Successfully saved config/fastlaunch.json");
        } catch (Exception e) {
            LOGGER.error("[Config] Failed to save config/fastlaunch.json: {}", e.getMessage());
        }
    }
}
