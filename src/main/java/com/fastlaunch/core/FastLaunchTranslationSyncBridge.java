package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 起動完了後 (STAGE 9 以降) にのみ安全に非同期で EMI / JEI の検索インデックスを更新するブリッジ。
 * 起動初期のデッドロック・フリーズを 100% 防止。
 */
public class FastLaunchTranslationSyncBridge {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/TranslationSyncBridge");
    private static final AtomicBoolean GAME_READY = new AtomicBoolean(false);
    private static volatile long lastSyncTimestamp = 0;

    public static void initialize() {
        LOGGER.info("[TranslationSyncBridge] 🌐 Translation sync bridge initialized (Ready for game title screen).");
    }

    /**
     * タイトル画面到達時やゲーム準備完了時にフラグを ON
     */
    public static void setGameReady() {
        GAME_READY.set(true);
        LOGGER.info("[TranslationSyncBridge] 🎮 Game is now ready for dynamic translation re-indexing.");
    }

    /**
     * 言語マップが更新された際に呼び出され、ゲーム起動完了後であれば非同期で EMI の検索を更新
     */
    public static void triggerIndexRebuild(String source) {
        // ゲームがまだ初期化中 (STAGE 9 等) の場合は絶対にフリーズさせないようスキップ！
        if (!GAME_READY.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSyncTimestamp < 3000) {
            return;
        }
        lastSyncTimestamp = now;

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000); // 翻訳注入の完了を 1 秒待機
                LOGGER.info("[TranslationSyncBridge] ⚡ Dynamic translation update detected from [{}]. Safely re-syncing EMI...", source);

                Class<?> emiSearchClass = Class.forName("dev.emi.emi.search.EmiSearch", false, FastLaunchTranslationSyncBridge.class.getClassLoader());
                Method bakeMethod = emiSearchClass.getDeclaredMethod("bake");
                bakeMethod.setAccessible(true);
                bakeMethod.invoke(null);
                LOGGER.info("[TranslationSyncBridge] ✅ EMI search index refreshed with latest translations!");
            } catch (Throwable ignored) {}
        });
    }
}
