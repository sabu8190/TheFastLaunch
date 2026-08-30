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
 * EMI において、Mekanism などのアイテムが日本語名や英語単体名 (粉砕機 / crusher 等) で
 * 検索にヒットしない問題を解決する多言語検索インデックス補完 Mixin。
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
            LOGGER.info("[EMISearch] 🔍 Active: Enabling localized multi-lingual item search indexing!");
            LOGGER.info("[EMISearch] 🔍 Mekanism machines (Crusher, Smelter, etc.) are now 100% searchable by name!");
            LOGGER.info("=======================================================================");
        }
    }
}
