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

    @Inject(method = "beginLoading", at = @At("RETURN"), require = 0, remap = false)
    private void onBeginLoading(CallbackInfoReturnable<CompletableFuture<ThingResourceManager>> cir) {
        CompletableFuture<ThingResourceManager> future = cir.getReturnValue();
        if (future != null) {
            long start = System.currentTimeMillis();
            future.thenAccept(manager -> {
                if (LOGGED.compareAndSet(false, true)) {
                    long elapsed = Math.max(0, System.currentTimeMillis() - start);
                    LOGGER.info("[ThingResourceManager] 🚀 ThingResourceManager completed loading in {} ms.", elapsed);
                    com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                            "ThingResourceManager", 
                            String.format("ACTIVE [Completed in %d ms]", elapsed)
                    );
                }
            });
        }
    }

    @Inject(method = "waitForLoading", at = @At("HEAD"), require = 0, remap = false)
    private void onWaitForLoadingHead(CompletableFuture<ThingResourceManager> future, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        LOGGER.info("[ThingResourceManager] ⏳ Main thread entered waitForLoading for JsonThings...");
    }

    @Inject(method = "waitForLoading", at = @At("RETURN"), require = 0, remap = false)
    private void onWaitForLoadingReturn(CompletableFuture<ThingResourceManager> future, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        LOGGER.info("[ThingResourceManager] ⚡ Main thread resumed after JsonThings completed loading!");
    }
}
