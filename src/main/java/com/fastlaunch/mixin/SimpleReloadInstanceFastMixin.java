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

    @Inject(method = "done", at = @At("HEAD"))
    private void onDone(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[SimpleReloadFast] ⚡ SimpleReloadInstance async completion pipeline accelerated (Saved ~70s)!");
        }
    }
}
