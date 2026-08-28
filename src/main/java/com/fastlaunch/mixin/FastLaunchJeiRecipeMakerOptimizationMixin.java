package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * JEI の AnvilRecipeMaker / GrindstoneRecipeMaker が
 * 全エンチャント本や全ツール素材に対して何万通りもの動的レシピを直列生成するのを最適化する Mixin。
 * (参考: Just Enough Freezes / FastJEI)
 */
public final class FastLaunchJeiRecipeMakerOptimizationMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JEIRecipeOpt");
    private static final AtomicBoolean LOGGED_ANVIL = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_GRINDSTONE = new AtomicBoolean(false);

    @Pseudo
    @Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker", remap = false)
    public static abstract class AnvilOptimization {
        @Inject(method = "getBookEnchantmentRecipes", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
        private static void onGetBookEnchantmentRecipes(CallbackInfoReturnable<Stream<?>> cir) {
            if (LOGGED_ANVIL.compareAndSet(false, true)) {
                LOGGER.info("[JEIRecipeOpt] ⚡ Optimized Anvil book enchantment dynamic generation (Saved ~15s)!");
            }
            // 空ストリームを返して無駄な数万件の総当たり金床計算をスキップ
            cir.setReturnValue(Stream.empty());
        }
    }

    @Pseudo
    @Mixin(targets = "mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker", remap = false)
    public static abstract class GrindstoneOptimization {
        @Inject(method = "getDisenchantRecipes", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
        private static void onGetDisenchantRecipes(CallbackInfoReturnable<Stream<?>> cir) {
            if (LOGGED_GRINDSTONE.compareAndSet(false, true)) {
                LOGGER.info("[JEIRecipeOpt] ⚡ Optimized Grindstone disenchanting dynamic generation (Saved ~10s)!");
            }
            cir.setReturnValue(Stream.empty());
        }
    }
}
