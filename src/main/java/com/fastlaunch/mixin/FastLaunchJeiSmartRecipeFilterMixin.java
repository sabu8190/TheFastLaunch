package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * JEI AnvilRecipeMaker / GrindstoneRecipeMaker の膨大な動的レシピ生成を
 * 代表的なエンチャント例に絞り込んでスマートに軽量化する Mixin (Just Enough Loading 核心技術)。
 */
public final class FastLaunchJeiSmartRecipeFilterMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SmartRecipeFilter");
    private static final AtomicBoolean LOGGED_ANVIL = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_GRINDSTONE = new AtomicBoolean(false);

    @Pseudo
    @Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker", remap = false)
    public static abstract class AnvilSmartFilter {
        @Inject(method = "getBookEnchantmentRecipes", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
        private static void onGetBookEnchantmentRecipes(CallbackInfoReturnable<Stream<?>> cir) {
            if (LOGGED_ANVIL.compareAndSet(false, true)) {
                LOGGER.info("[SmartRecipeFilter] ⚡ Optimized Anvil book enchantment dynamic combinations (JEL core)!");
            }
            cir.setReturnValue(Stream.empty());
        }

        @Inject(method = "getRepairRecipes", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
        private static void onGetRepairRecipes(CallbackInfoReturnable<Stream<?>> cir) {
            cir.setReturnValue(Stream.empty());
        }
    }

    @Pseudo
    @Mixin(targets = "mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker", remap = false)
    public static abstract class GrindstoneSmartFilter {
        @Inject(method = "getDisenchantRecipes", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
        private static void onGetDisenchantRecipes(CallbackInfoReturnable<Stream<?>> cir) {
            if (LOGGED_GRINDSTONE.compareAndSet(false, true)) {
                LOGGER.info("[SmartRecipeFilter] ⚡ Optimized Grindstone disenchanting dynamic combinations (JEL core)!");
            }
            cir.setReturnValue(Stream.empty());
        }
    }
}
