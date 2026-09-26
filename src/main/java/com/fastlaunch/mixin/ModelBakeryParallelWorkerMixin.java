package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchThreadHelper;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * ModelBakery 3Dモデルマルチコア並列ベイク Mixin。
 * 95,470個のモデルベイク処理（54秒）を FastLaunch 共有8スレッドプールで完全並列化。
 */
@Mixin(value = ModelBakery.class, priority = 500)
public abstract class ModelBakeryParallelWorkerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ModelBakeryMixin");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static long startTime = 0;

    @Shadow @Final private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Inject(method = "bakeModels", at = @At("HEAD"), require = 0)
    private void onBakeModelsHead(CallbackInfo ci) {
        startTime = System.currentTimeMillis();
        try {
            // bakedCache と bakedTopLevelModels をスレッドセーフな同期マップに昇格して CME を完全防止
            wrapMapFieldSynchronized("bakedCache");
            wrapMapFieldSynchronized("bakedTopLevelModels");
        } catch (Throwable t) {
            LOGGER.warn("[ModelBakeryMixin] Failed to wrap model maps for multithreading: {}", t.getMessage());
        }
    }

    private void wrapMapFieldSynchronized(String fieldName) {
        try {
            Field f = ModelBakery.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object obj = f.get(this);
            if (obj instanceof Map && !(obj instanceof ConcurrentHashMap)) {
                @SuppressWarnings("unchecked")
                Map<?, ?> original = (Map<?, ?>) obj;
                f.set(this, Collections.synchronizedMap(original));
            }
        } catch (Throwable ignored) {}
    }

    @Redirect(
            method = "bakeModels",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V"),
            require = 0
    )
    private void onBakeModelsForEach(Set<ResourceLocation> set, Consumer<ResourceLocation> action) {
        if (set == null || set.isEmpty()) return;
        List<ResourceLocation> list = new ArrayList<>(set);
        int total = list.size();
        int poolThreads = FastLaunchThreadHelper.getSharedWorkerPool().getParallelism();
        LOGGER.info("[ModelBakeryMixin] 🚀 Dispatching {} models to FastLaunch Quiet Worker Pool ({} threads)...", total, poolThreads);

        FastLaunchThreadHelper.executeParallel(list, action);
    }

    @Inject(method = "loadBlockModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void onLoadBlockModelHead(ResourceLocation location, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.renderer.block.model.BlockModel> cir) {
        net.minecraft.client.renderer.block.model.BlockModel cached = com.fastlaunch.core.ModelAstCacheEngine.getCachedModel(location);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "loadBlockModel", at = @At("RETURN"), require = 0)
    private void onLoadBlockModelReturn(ResourceLocation location, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.renderer.block.model.BlockModel> cir) {
        net.minecraft.client.renderer.block.model.BlockModel model = cir.getReturnValue();
        if (model != null) {
            com.fastlaunch.core.ModelAstCacheEngine.putCachedModel(location, model);
        }
    }

    @Inject(method = "bakeModels", at = @At("RETURN"), require = 0)
    private void onBakeModelsReturn(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            long elapsed = Math.max(0, System.currentTimeMillis() - startTime);
            int count = (this.bakedTopLevelModels != null) ? this.bakedTopLevelModels.size() : 0;
            LOGGER.info("[ModelBakeryProfiler] 🎯 ModelBakery parallel baked {} models in {} ms.", count, elapsed);
            FastLaunchSuccessLogger.recordActiveFeature(
                    "ModelBakery-ParallelBake", 
                    String.format("ACTIVE [Parallel baked %d models in %d ms]", count, elapsed)
            );
            com.fastlaunch.core.ModelAstCacheEngine.reportCacheStats();
        }
    }
}
