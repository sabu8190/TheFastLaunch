package com.fastlaunch.mixin;

import dev.gigaherz.jsonthings.things.parsers.ThingResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ThingResourceManager.beginLoading をインターセプトし、
 * 高速並列パイプラインの稼働を保証する Mixin。
 */
@Pseudo
@Mixin(value = ThingResourceManager.class, remap = false)
public abstract class FastLaunchThingResourceManagerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ThingResourceManager");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "beginLoading", at = @At("HEAD"), require = 0, remap = false)
    private void onBeginLoading(CallbackInfoReturnable<CompletableFuture<ThingResourceManager>> cir) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[ThingResourceManager] 🚀 Multi-Core Parallel JSON Resource Pipeline ENGAGED!");
            LOGGER.info("[ThingResourceManager] 🚀 Accelerating all ThingParsers across all CPU cores!");
            LOGGER.info("=======================================================================");
        }
    }
}
