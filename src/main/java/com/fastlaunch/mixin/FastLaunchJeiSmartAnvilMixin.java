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
 * JEI AnvilRecipeMaker の膨大な動的レシピ生成を軽量化する Mixin (JEL 核心)。
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker", remap = false)
public abstract class FastLaunchJeiSmartAnvilMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SmartAnvil");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "getBookEnchantmentRecipes", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void onGetBookEnchantmentRecipes(CallbackInfoReturnable<Stream<?>> cir) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[SmartAnvil] ⚡ Optimized Anvil book enchantment dynamic combinations (JEL core)!");
        }
        cir.setReturnValue(Stream.empty());
    }

    @Inject(method = "getRepairRecipes", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void onGetRepairRecipes(CallbackInfoReturnable<Stream<?>> cir) {
        cir.setReturnValue(Stream.empty());
    }
}
