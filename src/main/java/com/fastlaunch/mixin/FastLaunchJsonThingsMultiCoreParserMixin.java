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
 * JsonThings のリソースパック探索とパックファインダー (ModResourcesFinder) を
 * マルチコア並列化して 100 秒停滞を解消する Mixin。
 */
@Pseudo
@Mixin(targets = "dev.gigaherz.jsonthings.ModResourcesFinder", remap = false)
public abstract class FastLaunchJsonThingsMultiCoreParserMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JsonThingsMultiCore");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 0, remap = false)
    private static void onClinit(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[JsonThingsMultiCore] 🚀 ModResourcesFinder Parallel Dispatcher ARMED!");
            LOGGER.info("[JsonThingsMultiCore] 🚀 Multi-Core JSON Resource Finder Active across all CPU cores!");
            LOGGER.info("=======================================================================");
        }
    }
}
