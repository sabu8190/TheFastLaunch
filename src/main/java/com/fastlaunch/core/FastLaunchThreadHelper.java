package com.fastlaunch.core;

import com.fastlaunch.config.FastLaunchConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Minecraft / Forge 環境に最適化された軽量マルチコア並列プール管理クラス。
 * 複数のモジュールが別々に ForkJoinPool を生成してスレッド過多（100+スレッド）になるのを防ぎ、
 * 単一のマネージド並列プールを共有することでコンテキストスイッチとCPU負荷スパイクを大幅に抑制する。
 */
public class FastLaunchThreadHelper {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ThreadHelper");

    private static volatile ForkJoinPool SHARED_WORKER_POOL = null;

    /**
     * 全最適化モジュール（JsonThings, ModelBakery, TouhouLittleMaid, ldlib 等）共通の
     * 最適化マネージド並列ワーカープールを取得する。
     */
    public static ForkJoinPool getSharedWorkerPool() {
        if (SHARED_WORKER_POOL == null) {
            synchronized (FastLaunchThreadHelper.class) {
                if (SHARED_WORKER_POOL == null) {
                    int availableCores = Runtime.getRuntime().availableProcessors();
                    // コア数に応じた適正な並列度（スレッド肥大化・CPU枯渇を防止）
                    int parallelism = Math.max(2, Math.min(availableCores, FastLaunchConfig.PARALLEL_WORKER_THREADS));

                    ClassLoader contextCl = Thread.currentThread().getContextClassLoader();

                    SHARED_WORKER_POOL = new ForkJoinPool(
                            parallelism,
                            pool -> {
                                ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                                thread.setName("FastLaunch-SharedWorker-" + thread.getPoolIndex());
                                thread.setDaemon(true);
                                // メインスレッド（描画・GC）を阻害しないよう優先度をわずかに下げて軽量化
                                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                                if (contextCl != null) {
                                    thread.setContextClassLoader(contextCl);
                                }
                                return thread;
                            },
                            (t, e) -> LOGGER.warn("[ThreadHelper] Uncaught exception in thread {}: {}", t.getName(), e.getMessage()),
                            false
                    );

                    LOGGER.info("[ThreadHelper] 🚀 Initialized lightweight shared worker pool (Parallelism: {} threads on {} CPU cores, Priority: NORM-1)",
                            parallelism, availableCores);
                }
            }
        }
        return SHARED_WORKER_POOL;
    }

    /**
     * カスタム名プレフィックス付きのスレッドファクトリを生成する。
     */
    public static ForkJoinPool.ForkJoinWorkerThreadFactory createSafeFactory(String namePrefix) {
        ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        return pool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName(namePrefix + "-" + thread.getPoolIndex());
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            if (contextCl != null) {
                thread.setContextClassLoader(contextCl);
            }
            return thread;
        };
    }
}
