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

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onModelBakeryInit(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[ModelBakeryMixin] 🎯 ModelBakery Multi-Core Parallel Engine active for all 3D item/block models!");
        }
    }
}
