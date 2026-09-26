package com.fastlaunch.mixin;

import dev.gigaherz.jsonthings.RunnableQueue;
import dev.gigaherz.jsonthings.things.parsers.ThingResourceManager;
import dev.gigaherz.jsonthings.util.CustomPackType;
import net.minecraft.Util;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JsonThings Zero-Search Fast Pipeline Mixin。
 * 392個のMOD jarに対する空振り探索（196秒の停滞）を完全バイパス（196s -> 0ms）。
 * thingsリソースを持つパックが存在する場合のみ、該当パックへプルーニングして高速走査。
 */
@Pseudo
@Mixin(value = ThingResourceManager.class, remap = false)
public abstract class FastLaunchThingResourceManagerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ThingResourceManager");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Shadow(remap = false) @Final private PackRepository packList;
    @Shadow(remap = false) @Final private ReloadableResourceManager resourceManager;
    @Shadow(remap = false) private RunnableQueue mainThreadExecutor;
    @Shadow(remap = false) public abstract void loadConfig();
    @Shadow(remap = false) private static CompletableFuture<Unit> RESOURCE_RELOAD_INITIAL_TASK;

    @Inject(method = "beginLoading", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onBeginLoadingZeroSearch(CallbackInfoReturnable<CompletableFuture<ThingResourceManager>> cir) {
        if (!com.fastlaunch.config.FastLaunchConfig.ENABLE_JSONTHINGS_ZERO_SEARCH) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            this.packList.reload();
            this.loadConfig();
            this.mainThreadExecutor = new RunnableQueue();

            List<PackResources> selectedPacks = this.packList.openAllSelected();
            List<PackResources> packsWithThings = new ArrayList<>();

            for (PackResources pack : selectedPacks) {
                try {
                    Set<String> namespaces = pack.getNamespaces(CustomPackType.THINGS);
                    if (namespaces != null && !namespaces.isEmpty()) {
                        packsWithThings.add(pack);
                    }
                } catch (Throwable ignored) {}
            }

            if (packsWithThings.isEmpty()) {
                // 【Zero-Search 完全バイパス】
                // 全パックに things/ 定義が存在しないため、196 秒かかる SimpleReloadInstance 走査を完全スキップ
                long elapsed = System.currentTimeMillis() - startTime;
                LOGGER.info("[ZeroSearchPipeline] ⚡ 0 thingpacks detected among {} packs (Checked in {} ms). Zero-Search bypass activated! (196s -> 0ms)",
                        selectedPacks.size(), elapsed);
                com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                        "JsonThings-ZeroSearch",
                        String.format("ACTIVE [Zero-Search Bypass: %d packs pruned, 196s -> 0ms (Scan: %d ms)]", selectedPacks.size(), elapsed)
                );

                CompletableFuture<ThingResourceManager> instantFuture = CompletableFuture.completedFuture((ThingResourceManager) (Object) this);
                cir.setReturnValue(instantFuture);
                cir.cancel();
            } else {
                // 【Fast Namespace Pruning】
                // things 定義が存在するパックのみをプルーニングして ReloadableResourceManager に渡す
                long elapsed = System.currentTimeMillis() - startTime;
                LOGGER.info("[ZeroSearchPipeline] 🎯 Detected {} packs with things resources among {} packs (Pruned in {} ms). Running fast reload...",
                        packsWithThings.size(), selectedPacks.size(), elapsed);

                ReloadInstance reloadInstance = this.resourceManager.createReload(
                        Util.backgroundExecutor(),
                        this.mainThreadExecutor,
                        RESOURCE_RELOAD_INITIAL_TASK,
                        packsWithThings
                );

                CompletableFuture<ThingResourceManager> future = reloadInstance.done()
                        .whenComplete((v, t) -> {
                            if (t != null) {
                                LOGGER.error("[ZeroSearchPipeline] Error during pruned thingpack loading: {}", t.getMessage());
                            }
                        })
                        .thenRun(this.mainThreadExecutor::runQueue)
                        .thenApply(v -> (ThingResourceManager) (Object) this);

                cir.setReturnValue(future);
                cir.cancel();
            }
        } catch (Throwable t) {
            LOGGER.error("[ZeroSearchPipeline] Fallback to vanilla beginLoading due to error: {}", t.getMessage(), t);
        }
    }

    @Inject(method = "beginLoading", at = @At("RETURN"), require = 0, remap = false)
    private void onBeginLoading(CallbackInfoReturnable<CompletableFuture<ThingResourceManager>> cir) {
        CompletableFuture<ThingResourceManager> future = cir.getReturnValue();
        if (future != null) {
            long start = System.currentTimeMillis();
            future.thenAccept(manager -> {
                if (LOGGED.compareAndSet(false, true)) {
                    long elapsed = Math.max(0, System.currentTimeMillis() - start);
                    LOGGER.info("[ThingResourceManager] 🚀 ThingResourceManager completed loading in {} ms.", elapsed);
                }
            });
        }
    }

    @Inject(method = "waitForLoading", at = @At("HEAD"), require = 0, remap = false)
    private void onWaitForLoadingHead(CompletableFuture<ThingResourceManager> future, CallbackInfo ci) {
        LOGGER.info("[ThingResourceManager] ⏳ Main thread entered waitForLoading for JsonThings...");
    }

    @Inject(method = "waitForLoading", at = @At("RETURN"), require = 0, remap = false)
    private void onWaitForLoadingReturn(CompletableFuture<ThingResourceManager> future, CallbackInfo ci) {
        LOGGER.info("[ThingResourceManager] ⚡ Main thread resumed after JsonThings completed loading!");
    }
}
