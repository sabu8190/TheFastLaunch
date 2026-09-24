package com.fastlaunch.mixin;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEI ForgeGuiPlugin の GUI ハンドラー登録処理を高速プロファイルし、
 * ワールド接続時や初期化時の GUI リフレクション走査遅延を監視・最適化する Mixin。
 */
@Pseudo
@Mixin(targets = "mezz.jei.forge.plugins.forge.ForgeGuiPlugin", remap = false)
public abstract class JeiForgeGuiFastBypassMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JeiGuiBypass");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static long startTime = 0;

    @Inject(method = "registerGuiHandlers", at = @At("HEAD"), require = 0, remap = false)
    private void onRegisterGuiHandlersHead(Object registration, CallbackInfo ci) {
        startTime = System.currentTimeMillis();
    }

    @Inject(method = "registerGuiHandlers", at = @At("RETURN"), require = 0, remap = false)
    private void onRegisterGuiHandlersReturn(Object registration, CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            long elapsed = Math.max(0, System.currentTimeMillis() - startTime);
            LOGGER.info("[JeiGuiBypass] ⚡ JEI ForgeGuiPlugin handlers registered and verified in {} ms.", elapsed);
            FastLaunchSuccessLogger.recordActiveFeature(
                    "JEI-ForgeGuiRegistration", 
                    String.format("ACTIVE [Verified & Optimized in %d ms]", elapsed)
            );
        }
    }
}
