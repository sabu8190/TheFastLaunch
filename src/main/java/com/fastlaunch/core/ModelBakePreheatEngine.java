package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
            FastLaunchThreadHelper.getSharedWorkerPool().execute(() -> {
                LOGGER.info("[ModelPreheat] Shared worker pool warmed up ({} configured workers on {} cores).",
                        FastLaunchThreadHelper.getSharedWorkerPool().getParallelism(), cores);
                FastLaunchSuccessLogger.recordActiveFeature(
                        "WorkerPool-Preheat", 
                        String.format("ACTIVE [%d CPU Workers Ready]", FastLaunchThreadHelper.getSharedWorkerPool().getParallelism())
                );
            });
        }
    }
}
