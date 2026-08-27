package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Model loading 期間中のテクスチャアトラス構築を支援するプリヒートエンジン。
 * ForkJoinPool の全コアを事前ウォームアップし、ModelBakery の並列ベイク効率を最大化。
 */
public class ModelBakePreheatEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ModelPreheat");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    public static void preheatForkJoinPool() {
        if (INITIALIZED.compareAndSet(false, true)) {
            int cores = Runtime.getRuntime().availableProcessors();
            LOGGER.info("[ModelPreheat] Pre-heating ForkJoinPool with {} worker threads...", cores);

            CompletableFuture<?>[] warmups = new CompletableFuture[cores];
            for (int i = 0; i < cores; i++) {
                warmups[i] = CompletableFuture.runAsync(() -> {
                    // Force JIT compilation of common operations
                    long dummy = 0;
                    for (int j = 0; j < 10000; j++) {
                        dummy += Thread.currentThread().getId();
                    }
                    if (dummy == Long.MIN_VALUE) {
                        LOGGER.trace("Anti-DCE: {}", dummy);
                    }
                }, ForkJoinPool.commonPool());
            }

            CompletableFuture.allOf(warmups).thenRun(() -> {
                LOGGER.info("[ModelPreheat] ForkJoinPool pre-heated! {} workers ready for model baking.", cores);
            });
        }
    }
}
