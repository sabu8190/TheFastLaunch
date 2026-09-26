package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchThreadHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TACZ (Timeless and Classics Zero) のシングルスレッドアセットローダー (9.3秒) を
 * FastLaunch 共有8スレッドプールで完全並列化する Mixin。
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.resource.ClientAssetLoadDispatcher", remap = false)
public abstract class FastLaunchTaczAssetDispatcherMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/TACZOptimizer");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "executor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void onGetExecutor(CallbackInfoReturnable<ExecutorService> cir) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[TACZOptimizer] 🚀 Upgraded TACZ asset dispatcher to FastLaunch Shared Worker Pool (8 threads)!");
            com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                    "TACZ-AssetParallel", 
                    "ACTIVE [TACZ gunpack loader upgraded to 8-thread shared pool]"
            );
        }
        cir.setReturnValue(FastLaunchThreadHelper.getSharedWorkerPool());
    }
}
