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
 * Forge Version Check のネットワーク I/O を完全バイパスする Mixin。
 * System.setProperty だけでは実際の HTTP リクエストが走る場合があるため、
 * VersionChecker のエントリポイントを直接キャンセルして 0ms 化する。
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.fml.VersionChecker", remap = false)
public abstract class ForgeVersionCheckerBypassMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/VersionBypass");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "startVersionCheck", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void onStartVersionCheck(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[VersionBypass] Forge VersionChecker network I/O BYPASSED! (Saved ~5-10s)");
            LOGGER.info("=======================================================================");
        }
        ci.cancel();
    }
}
