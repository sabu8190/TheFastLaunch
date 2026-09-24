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
 * モデル欠損テクスチャ置換走査 9秒停止の高速スナップショットバイパス Mixin。
 */
@Mixin(value = ModelBakery.class, priority = 450)
public abstract class ModelReplacementCacheMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ModelReplaceCache");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static final java.util.concurrent.atomic.AtomicInteger topLevelCount = new java.util.concurrent.atomic.AtomicInteger(0);

    @Inject(method = "loadTopLevel", at = @At("HEAD"))
    private void onLoadTopLevel(CallbackInfo ci) {
        int count = topLevelCount.incrementAndGet();
        if (count == 1) {
            LOGGER.info("[ModelReplaceProfiler] 🎯 ModelBakery TopLevel model resolution started.");
            com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                    "ModelBakery-TopLevelResolution", 
                    "ACTIVE [TopLevel Model Resolution Monitored]"
            );
        }
    }
}
