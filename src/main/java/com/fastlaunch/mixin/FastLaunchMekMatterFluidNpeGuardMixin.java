package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mekanism Matter (mekmm) の TileEntityFluidReplicator.customRecipeMap が
 * null のまま参照されて JEI 登録時に NullPointerException が発生するのを防止する Mixin。
 */
@Pseudo
@Mixin(targets = "com.jerry.mekmm.common.tile.machine.TileEntityFluidReplicator", remap = false)
public abstract class FastLaunchMekMatterFluidNpeGuardMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/MekMatterFluidNPE");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Shadow public static HashMap<String, Integer> customRecipeMap;

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 0, remap = false)
    private static void onClinit(CallbackInfo ci) {
        if (customRecipeMap == null) {
            customRecipeMap = new HashMap<>();
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[MekMatterFluidNPE] 🛡️ Guarded TileEntityFluidReplicator.customRecipeMap against NPE!");
                LOGGER.info("=======================================================================");
            }
        }
    }
}
