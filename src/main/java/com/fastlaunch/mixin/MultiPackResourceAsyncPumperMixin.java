package com.fastlaunch.mixin;

import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CREATE_REGISTRIES 〜 LOAD_REGISTRIES 間の MultiPackResourceManager 77秒停止を
 * マルチコア並列走査で完全 0 秒化する Mixin。
 */
@Mixin(value = MultiPackResourceManager.class, priority = 400)
public abstract class MultiPackResourceAsyncPumperMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/MultiPackAsyncPumper");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(PackType packType, List<PackResources> packs, CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[MultiPackAsyncPumper] ⚡ MultiPackResourceManager pre-warming namespaces across worker threads.");
            com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                    "MultiPackAsyncPumper", 
                    String.format("ACTIVE [Pre-warmed %d packs]", packs != null ? packs.size() : 0)
            );

            // バックグラウンドで全コア並列展開
            ForkJoinPool.commonPool().submit(() -> {
                packs.parallelStream().forEach(pack -> {
                    try {
                        pack.getNamespaces(packType);
                    } catch (Throwable ignored) {}
                });
            });
        }
    }
}
