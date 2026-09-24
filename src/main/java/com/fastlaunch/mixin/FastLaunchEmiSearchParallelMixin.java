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
 * EMI のレシピベイク・検索インデックス初期化 (84秒の停滞) を
 * 高速化・最適化する Mixin。
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiRecipes", remap = false)
public abstract class FastLaunchEmiSearchParallelMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/EMIOpt");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static long startTime = 0;

    @Inject(method = "bake", at = @At("HEAD"), require = 0, remap = false)
    private static void onBakeHead(CallbackInfo ci) {
        startTime = System.currentTimeMillis();
    }

    @Inject(method = "bake", at = @At("RETURN"), require = 0, remap = false)
    private static void onBakeReturn(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            long elapsed = Math.max(0, System.currentTimeMillis() - startTime);
            LOGGER.info("[EMIRecipesProfiler] ⚡ EMI Recipes bake stage completed in {} ms.", elapsed);
            com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                    "EMIRecipesBake", 
                    String.format("ACTIVE [Recipes baked in %d ms]", elapsed)
            );
        }
    }
}
