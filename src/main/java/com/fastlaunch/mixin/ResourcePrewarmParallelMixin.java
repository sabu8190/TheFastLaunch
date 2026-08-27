package com.fastlaunch.mixin;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MultiPackResourceManager 構築直後にバックグラウンドで全パックの namespace を
 * 事前キャッシュすることで、後続のリソース探索を高速化する。
 * listResources 自体はオーバーライドしないため FancyMenu との完全互換性を維持。
 */
@Mixin(value = MultiPackResourceManager.class, priority = 1200)
public abstract class ResourcePrewarmParallelMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ResourcePrewarm");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Shadow @Final private List<PackResources> packs;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (this.packs != null && this.packs.size() > 10) {
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[ResourcePrewarm] ⚡ Background namespace prewarm for {} packs!", this.packs.size());
                LOGGER.info("[ResourcePrewarm] ⚡ Pre-caching all pack namespaces in parallel!");
                LOGGER.info("=======================================================================");
            }

            final List<PackResources> packsCopy = List.copyOf(this.packs);

            CompletableFuture.runAsync(() -> {
                packsCopy.parallelStream().forEach(pack -> {
                    try {
                        pack.getNamespaces(PackType.CLIENT_RESOURCES);
                    } catch (Throwable ignored) {}
                });
                LOGGER.info("[ResourcePrewarm] All {} pack namespaces pre-cached!", packsCopy.size());
            });
        }
    }
}
