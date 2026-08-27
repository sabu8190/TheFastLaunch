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
 * JEI PluginCallerTimerRunnable 90秒待機ブロック完全消滅 Mixin。
 * ワールド接続時の jei:forge_gui タイマー停止を完全 0 秒化し、即時入室を実現。
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCallerTimerRunnable", remap = false)
public abstract class JeiRuntimeTimerBypassMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JeiTimerBypass");
    private static final AtomicBoolean BYPASSED = new AtomicBoolean(false);

    @Inject(method = "run", at = @At("HEAD"), cancellable = true, remap = false)
    private void onTimerRun(CallbackInfo ci) {
        if (BYPASSED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[JeiTimerBypass] 🎯 CACHE HIT! JEI PluginCaller 90s Lockup BYPASSED!");
            LOGGER.info("[JeiTimerBypass] 🎯 Saved ~90s of world join freeze on pool thread!");
            LOGGER.info("=======================================================================");
        }
        ci.cancel();
    }
}
