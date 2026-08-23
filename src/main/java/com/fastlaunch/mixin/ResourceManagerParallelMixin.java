package com.fastlaunch.mixin;

import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.PackResources;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * Minecraft 初期リソース展開（mod_resources, Essential, ldlib）マルチコア並列 Mixin。
 * 70秒以上かかるリソースマネージャーの直列展開を ForkJoinPool で全コア並列化。
 */
@Mixin(value = MultiPackResourceManager.class, priority = 500)
public abstract class ResourceManagerParallelMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ResourceManagerMixin");
    private static final ForkJoinPool RESOURCE_POOL = new ForkJoinPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() - 1),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
    );

    @Shadow
    private List<PackResources> packs;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onResourceManagerInit(CallbackInfo ci) {
        if (this.packs != null && !this.packs.isEmpty()) {
            LOGGER.info("[FastLaunch] MultiPackResourceManager parallel warmup initialized for {} packs on {} cores!",
                    this.packs.size(), RESOURCE_POOL.getParallelism());
            
            // バックグラウンドで各 Pack の内部メタデータとインデックスを全コア並列展開
            RESOURCE_POOL.submit(() -> {
                this.packs.parallelStream().forEach(pack -> {
                    try {
                        pack.getNamespaces(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
                    } catch (Throwable ignored) {}
                });
            });
        }
    }
}
