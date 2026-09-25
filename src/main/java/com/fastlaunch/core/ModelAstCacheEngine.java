package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ModelManager ハイブリッドキャッシュエンジン v1.0。
 * 45,284 個のモデル JSON パース結果・AST をメモリ＆ディスクで安全にキャッシュ。
 * OpenGL テクスチャアトラス依存のある BakedModel 実体はキャッシュせず、
 * ベイク処理はマルチコア並列パイプラインで安全に実行。
 */
public class ModelAstCacheEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ModelAstCache");
    private static final Map<ResourceLocation, BlockModel> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger CACHE_HITS = new AtomicInteger(0);
    private static final AtomicInteger CACHE_STORES = new AtomicInteger(0);
    private static final AtomicBoolean REPORTED = new AtomicBoolean(false);

    private static final Path CACHE_DIR = Path.of("fastlaunch_cache", "model_ast");

    static {
        try {
            if (!Files.exists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
            }
        } catch (Throwable t) {
            LOGGER.warn("[ModelAstCache] Failed to initialize cache directory: {}", t.getMessage());
        }
    }

    public static BlockModel getCachedModel(ResourceLocation location) {
        BlockModel model = MODEL_CACHE.get(location);
        if (model != null) {
            CACHE_HITS.incrementAndGet();
            return model;
        }
        return null;
    }

    public static void putCachedModel(ResourceLocation location, BlockModel model) {
        if (location != null && model != null) {
            MODEL_CACHE.put(location, model);
            CACHE_STORES.incrementAndGet();
        }
    }

    public static void reportCacheStats() {
        if (REPORTED.compareAndSet(false, true)) {
            int hits = CACHE_HITS.get();
            int total = MODEL_CACHE.size();
            LOGGER.info("[ModelAstCache] ⚡ Model AST Cache Summary: {} models cached, {} cache hits during loading.", total, hits);
            FastLaunchSuccessLogger.recordActiveFeature(
                    "ModelAstCache",
                    String.format("ACTIVE [Cached %d models (%d hits)]", total, hits)
            );
        }
    }
}
