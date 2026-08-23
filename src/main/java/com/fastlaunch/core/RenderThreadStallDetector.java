package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * メインスレッド停止＆白画面（Ghost Window / 応答なし）リアルタイム検知センサー。
 * メインスレッドの停止を監視し、2.5秒（白画面接近）および 5.0秒（白画面発生）を検知して
 * 原因となっている Mod とスタックトレースをログに正確に記録する。
 */
public class RenderThreadStallDetector {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/StallDetector");
    private static final AtomicLong LAST_HEARTBEAT = new AtomicLong(System.currentTimeMillis());
    private static final AtomicBoolean STALL_LOGGED = new AtomicBoolean(false);
    private static volatile Thread renderThreadRef = null;

    private static final ScheduledExecutorService WATCHDOG_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FastLaunch-StallWatchdog");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean ARMED = new AtomicBoolean(false);

    public static void startMonitoring() {
        if (ARMED.compareAndSet(false, true)) {
            renderThreadRef = Thread.currentThread();
            LOGGER.info("[StallDetector] >>> Render Thread Stall & White Screen Real-Time Sensor ARMED! <<<");

            WATCHDOG_EXECUTOR.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                long elapsed = now - LAST_HEARTBEAT.get();

                if (elapsed >= 5000) { // 5秒以上：Windows が白画面（応答なし）を発動する基準
                    if (STALL_LOGGED.compareAndSet(false, true)) {
                        LOGGER.error("=======================================================================");
                        LOGGER.error("⚠️ [FastLaunch Sensor] WHITE SCREEN (NOT RESPONDING) DETECTED!");
                        LOGGER.error("⚠️ Render thread has been FROZEN for {} ms (> 5.0s)!", elapsed);
                        logStallCulprit();
                        LOGGER.error("=======================================================================");
                        System.err.println("[FastLaunch] ⚠️ WHITE SCREEN DETECTED: Render thread stalled for " + elapsed + " ms!");
                    }
                } else if (elapsed >= 2500) { // 2.5秒以上：白画面に近づいている警告
                    if (!STALL_LOGGED.get()) {
                        LOGGER.warn("[StallDetector] Potential White Screen imminent! Render thread stalled for {} ms...", elapsed);
                    }
                } else {
                    if (STALL_LOGGED.compareAndSet(true, false)) {
                        LOGGER.info("✅ [StallDetector] Render thread RECOVERED! Normal execution resumed.");
                        System.out.println("[FastLaunch] ✅ Render thread recovered from stall.");
                    }
                }
            }, 1000, 500, TimeUnit.MILLISECONDS);
        }
    }

    public static void updateHeartbeat() {
        LAST_HEARTBEAT.set(System.currentTimeMillis());
    }

    private static void logStallCulprit() {
        Thread t = renderThreadRef;
        if (t != null) {
            StackTraceElement[] stack = t.getStackTrace();
            LOGGER.error("--- Current Stack Trace of Blocking Render Thread ---");
            int count = 0;
            for (StackTraceElement elem : stack) {
                LOGGER.error("    at {}", elem.toString());
                if (++count >= 15) break;
            }
        }
    }
}
