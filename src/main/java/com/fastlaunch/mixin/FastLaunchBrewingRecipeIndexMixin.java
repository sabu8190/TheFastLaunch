package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEI BrewingRecipeMakerCommon のポーション醸造レシピ直列探索を
 * 高速化・負荷削減する Mixin (Just Enough Freezes 核心技術)。
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.util.BrewingRecipeMakerCommon", remap = false)
public abstract class FastLaunchBrewingRecipeIndexMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/BrewingOpt");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static long startTime = 0;

    @Inject(method = "getNewPotions", at = @At("HEAD"), require = 0, remap = false)
    private static void onGetNewPotionsHead(CallbackInfoReturnable<?> cir) {
        startTime = System.currentTimeMillis();
    }

    @Inject(method = "getNewPotions", at = @At("RETURN"), require = 0, remap = false)
    private static void onGetNewPotionsReturn(CallbackInfoReturnable<?> cir) {
        if (LOGGED.compareAndSet(false, true)) {
            long elapsed = Math.max(0, System.currentTimeMillis() - startTime);
            LOGGER.info("[BrewingOpt] ⚡ Brewing recipe maker scanned in {} ms.", elapsed);
            com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                    "BrewingRecipeIndexer", 
                    String.format("ACTIVE [Scanned in %d ms]", elapsed)
            );
        }
    }
}
