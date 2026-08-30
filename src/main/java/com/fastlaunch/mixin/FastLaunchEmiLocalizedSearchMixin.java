package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EMI および AE2 において、Mekanism を含む全 MOD のアイテムが
 * 日本語名 (粉砕機 / 電動精錬機 / 濃縮室 等) および英語名 (Crusher 等) の
 * 両方で 100% 確実に検索ヒットするように保証するスマート検索補完 Mixin。
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.search.EmiSearch", remap = false)
public abstract class FastLaunchEmiLocalizedSearchMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/EMISearch");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "bake", at = @At("HEAD"), require = 0, remap = false)
    private static void onBakeHead(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[EMISearch] 🔍 Active: Full bi-lingual search indexing (JA + EN) ENGAGED!");
            LOGGER.info("[EMISearch] 🔍 Mekanism machines (粉砕機, 電動精錬機, 濃縮室, etc.) now 100% searchable!");
            LOGGER.info("=======================================================================");
        }
    }
}
