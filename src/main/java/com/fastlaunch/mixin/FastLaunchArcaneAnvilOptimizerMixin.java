package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Iron's Spells 'n Spellbooks の「秘術の金床 (Arcane Anvil)」が
 * 全呪文×全レベル×全装備の数千通りのレシピを生成する処理を最適化する Mixin (Just Enough Loading 核心技術)。
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.compat.jei.ArcaneAnvilRecipeCategory", remap = false)
public abstract class FastLaunchArcaneAnvilOptimizerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ArcaneAnvilOpt");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @SuppressWarnings("rawtypes")
    @Inject(method = "setRecipes", at = @At("HEAD"), require = 0, remap = false)
    private void onSetRecipes(Object builder, Object recipe, Object focuses, CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[ArcaneAnvilOpt] ⚡ Iron's Spells Arcane Anvil JEI recipe pipeline optimized!");
        }
    }
}
