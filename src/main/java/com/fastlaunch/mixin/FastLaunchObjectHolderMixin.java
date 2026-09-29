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
 * ObjectHolderRegistry の走査時間を計測する軽量プロファイラー Mixin。
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.registries.ObjectHolderRegistry", remap = false)
public abstract class FastLaunchObjectHolderMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ObjectHolderOpt");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static long objectHolderStartTime = 0;

    @Inject(method = "findObjectHolders", at = @At("HEAD"), require = 0, remap = false)
    private static void onFindObjectHoldersHead(CallbackInfo ci) {
        objectHolderStartTime = System.currentTimeMillis();
    }

    @Inject(method = "findObjectHolders", at = @At("RETURN"), require = 0, remap = false)
    private static void onFindObjectHoldersReturn(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            long elapsed = Math.max(0, System.currentTimeMillis() - objectHolderStartTime);
            LOGGER.info("[ObjectHolderProfiler] ⚡ ObjectHolderRegistry scan completed in {} ms.", elapsed);
            FastLaunchSuccessLogger.recordActiveFeature(
                    "ObjectHolderScan", 
                    String.format("ACTIVE [Scanned in %d ms]", elapsed)
            );
        }
    }
}
