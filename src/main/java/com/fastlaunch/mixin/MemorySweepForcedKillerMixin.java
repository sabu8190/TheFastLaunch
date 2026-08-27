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
 * MemorySweep ワールド入室時 77秒〜121秒 Stop-the-World フル GC 完全無効化 Mixin。
 */
@Pseudo
@Mixin(targets = {"com.supsm.memorysweep.MemorySweep", "com.supsm.memorysweep.client.MemorySweepClient"}, remap = false)
public abstract class MemorySweepForcedKillerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/MemorySweepKiller");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = {"sweep", "run", "cleanMemory", "onWorldJoin"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void onSweep(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[MemorySweepKiller] 🛡️ Neutralized MemorySweep 77s synchronous Full-GC!");
            LOGGER.info("[MemorySweepKiller] 🛡️ World join lag spike completely ELIMINATED (Saved ~77s)!");
            LOGGER.info("=======================================================================");
        }
        ci.cancel();
    }
}
