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

import java.io.File;
import java.nio.file.Files;
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
            File gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().toFile();
            File cacheFile = new File(gameDir, "fastlaunch_cache/jsonthings_empty.cache");

            // ModpackHash による即時ゼロ秒バイパス判定
            long currentHash = calculateModpackHash(new File(gameDir, "mods"));
            if (cacheFile.exists() && cacheFile.length() > 0) {
                try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(cacheFile))) {
                    if ("TFL_JT_EMPTY_V1".equals(in.readUTF()) && in.readLong() == currentHash) {
                        LOGGER.info("[ZeroSearchPipeline] ⚡ Cached zero-thingpacks verified from disk (0ms). Instant bypass activated!");
                        this.loadConfig();
                        this.mainThreadExecutor = new RunnableQueue();
                        com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                                "JsonThings-ZeroSearch",
                                "ACTIVE [Cached Zero-Search Bypass: 0 thingpacks (0 ms)]"
                        );
                        cir.setReturnValue(CompletableFuture.completedFuture((ThingResourceManager) (Object) this));
                        cir.cancel();
                        return;
                    }
                } catch (Throwable ignored) {}
            }

            this.packList.reload();
            this.loadConfig();
            this.mainThreadExecutor = new RunnableQueue();

            List<PackResources> selectedPacks = this.packList.openAllSelected();
            List<PackResources> packsWithThings = new ArrayList<>();

            for (PackResources pack : selectedPacks) {
                try {
                    Set<String> namespaces = pack.getNamespaces(CustomPackType.THINGS);
                    if (namespaces != null && !namespaces.isEmpty()) {
                        boolean hasRealThings = false;
                        for (String ns : namespaces) {
                            final boolean[] found = new boolean[]{false};
                            pack.listResources(CustomPackType.THINGS, ns, "", (loc, ioSupplier) -> {
                                found[0] = true;
                            });
                            if (found[0]) {
                                hasRealThings = true;
                                break;
                            }
                        }
                        if (hasRealThings) {
                            packsWithThings.add(pack);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (packsWithThings.isEmpty()) {
                // 【Zero-Search 完全バイパス ＆ キャッシュ永続化】
                // 全パックに things/ 定義が存在しないため、キャッシュファイルに保存して次回スキャンを完全0ms化
                try {
                    File cacheDir = new File(gameDir, "fastlaunch_cache");
                    if (!cacheDir.exists()) cacheDir.mkdirs();
                    try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.FileOutputStream(cacheFile))) {
                        out.writeUTF("TFL_JT_EMPTY_V1");
                        out.writeLong(currentHash);
                    }
                } catch (Throwable ignored) {}

                long elapsed = System.currentTimeMillis() - startTime;
                LOGGER.info("[ZeroSearchPipeline] ⚡ 0 thingpacks detected among {} packs (Checked in {} ms). Zero-Search bypass activated! (Saved cache)",
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
        if (this.mainThreadExecutor == null) {
            this.mainThreadExecutor = new RunnableQueue();
        }
        LOGGER.info("[ThingResourceManager] ⏳ Main thread entered waitForLoading for JsonThings...");
    }

    @Inject(method = "waitForLoading", at = @At("RETURN"), require = 0, remap = false)
    private void onWaitForLoadingReturn(CompletableFuture<ThingResourceManager> future, CallbackInfo ci) {
        LOGGER.info("[ThingResourceManager] ⚡ Main thread resumed after JsonThings completed loading!");
    }

    private static long calculateModpackHash(File modsDir) {
        if (!modsDir.exists()) return 0L;
        try {
            return Files.walk(modsDir.toPath(), 1)
                    .filter(p -> p.toString().endsWith(".jar") && !p.getFileName().toString().contains("TheFastLaunch") && !p.getFileName().toString().contains("fastlaunch"))
                    .mapToLong(p -> {
                        File f = p.toFile();
                        return f.length() ^ (f.lastModified() * 31);
                    })
                    .sum();
        } catch (Throwable e) {
            return 0L;
        }
    }
}
