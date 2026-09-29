package com.fastlaunch.mixin;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraftforge.registries.GameData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * State transition LOAD_REGISTRIES をプロファイリングする Mixin。
 */
@Mixin(value = GameData.class, priority = 500, remap = false)
public abstract class LoadRegistriesParallelDispatcherMixin {
    private static final Logger FAST_LOGGER = LogManager.getLogger("FastLaunch/LoadRegistriesParallel");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static long postRegisterStartTime = 0;

    @Inject(method = "postRegisterEvents", at = @At("HEAD"), remap = false)
    private static void onPostRegisterEventsHead(CallbackInfo ci) {
        postRegisterStartTime = System.currentTimeMillis();
    }

    @Inject(method = "postRegisterEvents", at = @At("RETURN"), remap = false)
    private static void onPostRegisterEventsReturn(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            long elapsed = Math.max(0, System.currentTimeMillis() - postRegisterStartTime);
            FAST_LOGGER.info("[LoadRegistriesProfiler] ⚡ GameData postRegisterEvents completed in {} ms.", elapsed);
            FastLaunchSuccessLogger.recordActiveFeature(
                    "LoadRegistriesStage", 
                    String.format("ACTIVE [Completed in %d ms]", elapsed)
            );
        }
    }
}
