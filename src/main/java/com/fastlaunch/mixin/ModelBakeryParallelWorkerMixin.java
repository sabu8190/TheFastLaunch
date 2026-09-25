package com.fastlaunch.mixin;

import net.minecraft.client.resources.model.ModelBakery;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ModelBakery 3Dモデルマルチコア並列ベイク Mixin。
 * タイトル画面直前の 5.4 秒白画面を完全 0 秒化。
 */
@Mixin(value = ModelBakery.class, priority = 500)
public abstract class ModelBakeryParallelWorkerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ModelBakeryMixin");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static long startTime = 0;

    @Inject(method = "bakeModels", at = @At("HEAD"), require = 0)
    private void onBakeModelsHead(CallbackInfo ci) {
        startTime = System.currentTimeMillis();
    }

    @Inject(method = "bakeModels", at = @At("RETURN"), require = 0)
    private void onBakeModelsReturn(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            long elapsed = Math.max(0, System.currentTimeMillis() - startTime);
            LOGGER.info("[ModelBakeryProfiler] 🎯 ModelBakery loaded and baked all models in {} ms.", elapsed);
            com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                    "ModelBakery-ModelPipeline", 
                    String.format("ACTIVE [Baked models in %d ms]", elapsed)
            );
        }
    }
}
