package com.fastlaunch.core;

import com.fastlaunch.config.FastLaunchConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.Consumer;

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
                    // コア数に応じた適正な並列度（スレッド肥大化・CPU枯渇を防止：デフォルト最大6スレッド）
                    int parallelism = Math.max(2, Math.min(availableCores, FastLaunchConfig.PARALLEL_WORKER_THREADS));

                    ClassLoader contextCl = Thread.currentThread().getContextClassLoader();

                    SHARED_WORKER_POOL = new ForkJoinPool(
                            parallelism,
                            pool -> {
                                ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                                thread.setName("FastLaunch-SharedWorker-" + thread.getPoolIndex());
                                thread.setDaemon(true);
                                // メインスレッド（描画・GC）を阻害しないよう優先度を下げて軽量化
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
     * ForkJoinPool.commonPool() を動員せず、指定したマネージドプール内でのみ
     * 均等チャンク分割して安全・軽量に並列実行する。
     * 128件ごとに Thread.yield() を挟み、CPU 100% 張り付きを確実に防止する。
     */
    public static <T> void executeParallel(Collection<T> items, Consumer<T> action) {
        if (items == null || items.isEmpty()) return;
        List<T> list = (items instanceof List) ? (List<T>) items : new ArrayList<>(items);
        int size = list.size();
        if (size <= 4) {
            for (T item : list) {
                action.accept(item);
            }
            return;
        }

        ForkJoinPool pool = getSharedWorkerPool();
        int threads = pool.getParallelism();
        int chunkSize = Math.max(1, (size + threads - 1) / threads);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < size; i += chunkSize) {
            final int start = i;
            final int end = Math.min(size, i + chunkSize);
            futures.add(CompletableFuture.runAsync(() -> {
                for (int j = start; j < end; j++) {
                    try {
                        action.accept(list.get(j));
                    } catch (Throwable t) {
                        LOGGER.warn("[ThreadHelper] Error processing parallel task item: {}", t.getMessage());
                    }
                    if ((j & 0x7F) == 0) {
                        Thread.yield(); // CPU占有を適度に解放
                    }
                }
            }, pool));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
