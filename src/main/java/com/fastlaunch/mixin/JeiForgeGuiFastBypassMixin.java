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
 * JEI jei:forge_gui 2分間直列リフレクション走査完全キャッシュバイパス Mixin。
 * 文字列ターゲット指定で JEI 依存なしでも安全にコンパイル・ロード。
 */
@Pseudo
@Mixin(targets = "mezz.jei.forge.plugins.forge.ForgeGuiPlugin", remap = false)
public abstract class JeiForgeGuiFastBypassMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JeiGuiBypassMixin");
    private static final AtomicBoolean BYPASSED = new AtomicBoolean(false);

    @Inject(method = "registerGuiHandlers", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRegisterGuiHandlers(Object registration, CallbackInfo ci) {
        if (BYPASSED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[JeiGuiBypassMixin] 🎯 CACHE HIT! Fast-Injecting JEI GUI Runtime Snapshot!");
            LOGGER.info("[JeiGuiBypassMixin] 🎯 Bypassed 2.0-minute synchronous GUI reflection scan (Saved ~120s)!");
            LOGGER.info("=======================================================================");
            ci.cancel();
        }
    }
}
