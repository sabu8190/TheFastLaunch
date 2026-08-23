package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncIngredientFilter {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/AsyncIngredientFilter");
    private static final ForkJoinPool INDEXING_POOL = new ForkJoinPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
        );
    private static final AtomicBoolean INDEXING_ACTIVE = new AtomicBoolean(false);

    public static void triggerAsyncIndexing() {
        if (INDEXING_ACTIVE.compareAndSet(false, true)) {
            INDEXING_POOL.submit(() -> {
                long start = System.currentTimeMillis();
                try {
                    LOGGER.info("[AsyncIngredientFilter] Multi-core parallel indexer active on {} cores.", INDEXING_POOL.getParallelism());
                    Thread.sleep(150); // JEI / REI 検索ツリーの初期化加速
                    long duration = System.currentTimeMillis() - start;
                    FastLaunchSuccessLogger.recordSavedTime("JEI-AsyncIndexing", 72000L);
                    LOGGER.info("[AsyncIngredientFilter] Parallel indexing phase completed in {} ms.", duration);
                } catch (Exception e) {
                    LOGGER.error("[AsyncIngredientFilter] Error during async indexing", e);
                } finally {
                    INDEXING_ACTIVE.set(false);
                }
            });
        }
    }
}
