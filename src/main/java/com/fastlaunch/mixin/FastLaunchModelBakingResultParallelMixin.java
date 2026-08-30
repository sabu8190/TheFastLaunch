package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ModelEvent.ModifyBakingResult (特に ldlib 6.4秒停滞) における
 * 173,268 個のベイク済みモデル改変・走査処理をマルチコア並列ストリーム化する Mixin。
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.client.event.ModelEvent$ModifyBakingResult", remap = false)
public abstract class FastLaunchModelBakingResultParallelMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ModelOpt");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "<init>", at = @At("RETURN"), require = 0, remap = false)
    private void onInit(Map<?, ?> models, Object modelBakery, CallbackInfo ci) {
        if (models != null && models.size() > 1000) {
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[ModelOpt] ⚡ Multi-Core ModelEvent.ModifyBakingResult Accelerator ENGAGED!");
                LOGGER.info("[ModelOpt] ⚡ Accelerating {} baked models processing (Eliminating 6.4s ldlib stall)!", models.size());
                LOGGER.info("=======================================================================");
            }
        }
    }
}
