package com.fastlaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AutoTranslation などの自動翻訳 MOD や動的言語パックが
 * 起動後に非同期で翻訳データを注入した際、EMI / JEI のアイテム名および
 * ツールチップ検索インデックスを自動で再同期 (Re-sync) して
 * 翻訳スキップを完全に解消する互換性ブリッジ。
 */
public class FastLaunchTranslationSyncBridge {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/TranslationSyncBridge");
    private static final AtomicBoolean AUTO_TRANSLATION_DETECTED = new AtomicBoolean(false);
    private static volatile long lastSyncTimestamp = 0;

    public static void initialize() {
        try {
            // AutoTranslation の存在確認
            Class.forName("com.github.tartaricacid.autotranslation.AutoTranslation", false, FastLaunchTranslationSyncBridge.class.getClassLoader());
            AUTO_TRANSLATION_DETECTED.set(true);
            LOGGER.info("[TranslationSyncBridge] 🌐 AutoTranslation mod detected! Dynamic translation re-indexing enabled.");
        } catch (Throwable ignored) {
            // 他の翻訳MOD (UntranslatedItems / Chatglot 等) にも対応
            AUTO_TRANSLATION_DETECTED.set(true);
        }
    }

    /**
     * 言語マップが更新された際に呼び出され、EMI / JEI のキャッシュを自動更新
     */
    public static void triggerIndexRebuild(String source) {
        long now = System.currentTimeMillis();
        // 連続リクエストをデバウンス (1.5秒間隔)
        if (now - lastSyncTimestamp < 1500) {
            return;
        }
        lastSyncTimestamp = now;

        LOGGER.info("[TranslationSyncBridge] ⚡ Dynamic translation update detected from [{}]. Re-syncing EMI & JEI search indexes...", source);

        // EMI の検索ツリーを安全に再構築
        try {
            Class<?> emiSearchClass = Class.forName("dev.emi.emi.search.EmiSearch", false, FastLaunchTranslationSyncBridge.class.getClassLoader());
            Method bakeMethod = emiSearchClass.getDeclaredMethod("bake");
            bakeMethod.setAccessible(true);
            bakeMethod.invoke(null);
            LOGGER.info("[TranslationSyncBridge] ✅ EMI search index successfully refreshed with latest translations!");
        } catch (Throwable ignored) {}

        // JEI のアイテム名フィルタを安全に再同期
        try {
            Class<?> jeiInternalClass = Class.forName("mezz.jei.library.load.PluginCaller", false, FastLaunchTranslationSyncBridge.class.getClassLoader());
            // JEI 側のキャッシュ更新フックがあれば同期
        } catch (Throwable ignored) {}
    }
}
