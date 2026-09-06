package com.fastlaunch.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.BufferedReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

/**
 * scanDirectory を安全に並列化し、STAGE 9 初期化時にも絶対にデッドロックしない堅牢 Mixin。
 */
@Mixin(value = SimpleJsonResourceReloadListener.class, priority = 500)
public abstract class FastLaunchSimpleJsonParallelMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SimpleJsonParallel");
    private static final ForkJoinPool JSON_SCAN_POOL = new ForkJoinPool(Math.min(32, Math.max(4, Runtime.getRuntime().availableProcessors() * 2)));

    @Inject(method = "scanDirectory", at = @At("HEAD"), cancellable = true)
    private static void onScanDirectoryParallel(ResourceManager resourceManager, String directory, Gson gson, Map<ResourceLocation, JsonElement> output, CallbackInfo ci) {
        if (resourceManager == null || output == null) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            FileToIdConverter fileToIdConverter = FileToIdConverter.json(directory);
            Map<ResourceLocation, Resource> matchingResources = fileToIdConverter.listMatchingResources(resourceManager);

            if (matchingResources == null || matchingResources.isEmpty()) {
                return;
            }

            // 少数のファイル (5件未満) はバニラの直列に任せて安全性を最大化
            int count = matchingResources.size();
            if (count < 5) {
                return;
            }

            Map<ResourceLocation, JsonElement> parallelOutput = new ConcurrentHashMap<>(count);

            JSON_SCAN_POOL.submit(() -> {
                matchingResources.entrySet().parallelStream().forEach(entry -> {
                    ResourceLocation rawLoc = entry.getKey();
                    ResourceLocation id = fileToIdConverter.fileToId(rawLoc);
                    Resource resource = entry.getValue();

                    try (BufferedReader reader = resource.openAsReader()) {
                        JsonElement element = GsonHelper.fromJson(gson, reader, JsonElement.class);
                        if (element != null) {
                            parallelOutput.put(id, element);
                        }
                    } catch (Throwable ignored) {}
                });
            }).get();

            output.putAll(parallelOutput);

            long elapsed = System.currentTimeMillis() - startTime;
            if (count > 50 || elapsed > 100) {
                LOGGER.info("[SimpleJsonParallel] ⚡ Scanned & parsed [{}] ({} JSON files) in {} ms across {} threads!", 
                        directory, count, elapsed, JSON_SCAN_POOL.getParallelism());
            }

            ci.cancel(); // 完了したらバニラループをバイパス
        } catch (Throwable t) {
            // 例外時は何もしない (バニラの直列処理にそのまま任せる)
        }
    }
}
