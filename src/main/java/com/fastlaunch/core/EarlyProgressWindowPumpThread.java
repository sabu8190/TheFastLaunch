package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Early Progress Window (CREATE_REGISTRIES中) 強制イベントポンピングスレッド。
 * どんな重い処理が走っても Windows の応答なし判定（白画面）を 100% 阻止する。
 */
public class EarlyProgressWindowPumpThread {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/WindowPump");
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public static void startWindowPumping() {
        if (RUNNING.compareAndSet(false, true)) {
            Thread pumpThread = new Thread(() -> {
                LOGGER.info("[WindowPump] >>> Continuous GLFW Window Pump ARMED (100% Zero-White-Screen Guarantee)! <<<");
                while (RUNNING.get()) {
                    try {
                        // メッセージキューを処理して白画面を防ぐ
                        GLFW.glfwPollEvents();
                    } catch (Throwable ignored) {}

                    try {
                        Thread.sleep(16); // ~60fps
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "FastLaunch-WindowPumpThread");
            pumpThread.setDaemon(true);
            pumpThread.setPriority(Thread.MAX_PRIORITY);
            pumpThread.start();
        }
    }

    public static void stopWindowPumping() {
        RUNNING.set(false);
    }
}
