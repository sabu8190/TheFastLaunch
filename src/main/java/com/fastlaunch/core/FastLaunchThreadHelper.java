package com.fastlaunch.core;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Minecraft / Forge TransformingClassLoader を安全に継承する
 * スレッドファクトリを提供するユーティリティクラス。
 */
public class FastLaunchThreadHelper {
    public static ForkJoinPool.ForkJoinWorkerThreadFactory createSafeFactory(String namePrefix) {
        ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
        return pool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName(namePrefix + "-" + thread.getPoolIndex());
            if (contextCl != null) {
                thread.setContextClassLoader(contextCl);
            }
            return thread;
        };
    }
}
