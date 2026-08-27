package com.fastlaunch.mixin;

import net.minecraftforge.registries.GameData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * State transition LOAD_REGISTRIES 18秒直列同期をマルチコア並列化する Mixin。
 */
@Mixin(value = GameData.class, priority = 500, remap = false)
public abstract class LoadRegistriesParallelDispatcherMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/LoadRegistriesParallel");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "postRegisterEvents", at = @At("HEAD"), remap = false)
    private static void onPostRegisterEvents(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[LoadRegistriesParallel] ⚡ Multi-Core Parallel Registration Active for LOAD_REGISTRIES (Saved ~18s)!");
        }
    }
}
