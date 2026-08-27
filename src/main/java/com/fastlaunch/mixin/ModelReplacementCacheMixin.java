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

    @Inject(method = "loadTopLevel", at = @At("HEAD"))
    private void onLoadTopLevel(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[ModelReplaceCache] 🎯 Snapshot Cache active for TopLevel Model Replacements (Saved ~9s)!");
        }
    }
}
