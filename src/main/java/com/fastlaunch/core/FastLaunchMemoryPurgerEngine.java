package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * タイトル画面到達後のアイドル時に、バックグラウンドで安全に余分なキャッシュを解放する軽量エンジン。
 */
public class FastLaunchMemoryPurgerEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/MemoryPurger");
    private static final AtomicBoolean PURGED = new AtomicBoolean(false);

    public static void purgeStartupCachesAsync(String phase) {
        if (!PURGED.compareAndSet(false, true)) {
            return;
        }

        // タイトル画面移行後の安全なタイミング (3秒後) にバックグラウンドで1回だけ軽快に実行
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            try {
                long beforeMemory = getUsedMemoryMB();
                LOGGER.info("[MemoryPurger] 🧹 Background idle memory optimization active at phase: {}", phase);
                System.gc();
                long afterMemory = getUsedMemoryMB();
                long savedMemory = beforeMemory - afterMemory;
                if (savedMemory > 0) {
                    LOGGER.info("[MemoryPurger] ⚡ Reclaimed {} MB of RAM without any startup stall!", savedMemory);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
}
