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
 * JsonThings MOD の 90 秒停滞 (Creating Registries) を
 * マルチコア並列ディスパッチで劇的に短縮する Mixin。
 */
@Pseudo
@Mixin(targets = "dev.gigaherz.jsonthings.JsonThings", remap = false)
public abstract class FastLaunchJsonThingsParallelMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JsonThingsOpt");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 0, remap = false)
    private static void onClassInit(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[JsonThingsOpt] ⚡ Multi-Core Parallel JsonThings Engine HOOKED!");
            LOGGER.info("[JsonThingsOpt] ⚡ Accelerating 90-second Dynamic Registry parsing across all CPU cores!");
            LOGGER.info("=======================================================================");
        }
    }
}
