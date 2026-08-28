package com.fastlaunch.core;

import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FancyMenu の日本語/CJKフォント (Noto Sans) アトラス初期化 (81秒のフリーズ) を
 * 起動直後にバックグラウンドで事前非同期ウォームアップするエンジン。
 */
public class FastLaunchFancyMenuFontPrewarmEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/FancyMenuPrewarm");
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    public static void prewarmFancyMenuFontsAsync() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                if (ModList.get().isLoaded("fancymenu")) {
                    LOGGER.info("[FancyMenuPrewarm] ⚡ Pre-warming FancyMenu SmoothFonts (Noto Sans) in background...");
                    Class<?> smoothFontsClass = Class.forName("de.keksuccino.fancymenu.util.rendering.text.smooth.SmoothFonts");
                    Object supplier = smoothFontsClass.getField("NOTO_SANS").get(null);
                    if (supplier instanceof java.util.function.Supplier<?> s) {
                        s.get(); // バックグラウンドでアトラス生成を事前完了
                        LOGGER.info("[FancyMenuPrewarm] ⚡ FancyMenu font atlas pre-warmed successfully (Saved ~81s stall)!");
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("[FancyMenuPrewarm] Notice: " + t.getMessage());
            }
        });
    }
}
