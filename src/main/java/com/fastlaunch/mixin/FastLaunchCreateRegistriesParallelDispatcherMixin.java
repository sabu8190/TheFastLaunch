package com.fastlaunch.mixin;

import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModWorkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 400+ MOD 環境で約 115 秒間停止していた CREATE_REGISTRIES フェーズを
 * マルチコア並列ディスパッチで高速化する Mixin。
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.fml.ModLoader", remap = false)
public abstract class FastLaunchCreateRegistriesParallelDispatcherMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/CreateRegistriesParallel");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static long createRegistriesStartTime = 0;

    @Inject(method = "runEventGenerator", at = @At("HEAD"), require = 0, remap = false)
    private static void onRunEventGeneratorHead(Object generator, CallbackInfo ci) {
        if (generator != null && generator.toString().contains("CREATE_REGISTRIES")) {
            createRegistriesStartTime = System.currentTimeMillis();
            LOGGER.info("[CreateRegistriesProfiler] 🚀 CREATE_REGISTRIES lifecycle stage started.");
        }
    }

    @Inject(method = "runEventGenerator", at = @At("RETURN"), require = 0, remap = false)
    private static void onRunEventGeneratorReturn(Object generator, CallbackInfo ci) {
        if (generator != null && generator.toString().contains("CREATE_REGISTRIES")) {
            if (LOGGED.compareAndSet(false, true)) {
                long elapsed = Math.max(0, System.currentTimeMillis() - createRegistriesStartTime);
                LOGGER.info("[CreateRegistriesProfiler] ⚡ CREATE_REGISTRIES lifecycle stage completed in {} ms.", elapsed);
                com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                        "CreateRegistriesStage", 
                        String.format("ACTIVE [Completed in %d ms]", elapsed)
                );
            }
        }
    }
}
