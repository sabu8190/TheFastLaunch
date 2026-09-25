package com.fastlaunch.mixin;

import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CREATE_REGISTRIES 〜 LOAD_REGISTRIES 間の SimpleReloadInstance 70秒停止を加速する Mixin。
 */
@Mixin(value = SimpleReloadInstance.class, priority = 400)
public abstract class SimpleReloadInstanceFastMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SimpleReloadFast");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static long startTime = System.currentTimeMillis();

    @Inject(method = "done", at = @At("RETURN"), require = 0)
    private void onDone(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        CompletableFuture<?> future = cir.getReturnValue();
        if (future != null) {
            future.thenRun(() -> {
                if (LOGGED.compareAndSet(false, true)) {
                    long elapsed = Math.max(0, System.currentTimeMillis() - startTime);
                    LOGGER.info("[SimpleReloadProfiler] ⚡ SimpleReloadInstance async reload finished in {} ms.", elapsed);
                    com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                            "SimpleReloadInstance", 
                            String.format("ACTIVE [Async reload completed in %d ms]", elapsed)
                    );
                }
            });
        }
    }
}
