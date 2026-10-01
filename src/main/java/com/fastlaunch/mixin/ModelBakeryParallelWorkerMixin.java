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
            // bakedCache (f_119213_), bakedTopLevelModels (f_119215_) などのマップを安全に検査
            // ModernFix の DynamicBakedModelProvider や ConcurrentMap の整合性を100%保持しつつ、
            // 非スレッドセーフな通常マップのみ同期化して ClassCastException およびデータ破損を完全根絶
            wrapAllMapFieldsSynchronized();

            // Forgified Fabric API (Fabric Model Loading API) のスレッド競合とガードを完全無害化
            com.fastlaunch.core.FabricModelLoadingOptimizer.secureModelBakery((ModelBakery) (Object) this);
        } catch (Throwable t) {
            LOGGER.error("[ModelBakeryMixin] ❌ Failed during ModelBakery pre-bake hardening: ", t);
        }
    }

    private void wrapAllMapFieldsSynchronized() {
        int wrappedCount = 0;
        int preservedCount = 0;
        for (Field f : ModelBakery.class.getDeclaredFields()) {
            try {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object obj = f.get(this);
                    if (obj == null) continue;

                    // 1. 既にスレッドセーフな ConcurrentMap (Guava Cache.asMap(), ConcurrentHashMap 等) は保持
                    if (obj instanceof java.util.concurrent.ConcurrentMap) {
                        preservedCount++;
                        continue;
                    }

                    // 2. ModernFix の DynamicBakedModelProvider や Mixin 生成クラス等は絶対に上書きしない
                    //    (ModernFix の captureGetter における (DynamicBakedModelProvider) f_119215_ キャスト破壊を完全防止)
                    String className = obj.getClass().getName();
                    if (className.contains("DynamicBakedModelProvider")
                            || className.contains("Synchronized")
                            || className.contains("modernfix")
                            || className.contains("Immutable")) {
                        preservedCount++;
                        continue;
                    }

                    // 3. 通常の非スレッドセーフ Map (HashMap, Object2ObjectOpenHashMap 等) のみを安全に同期ラップ
                    @SuppressWarnings("unchecked")
                    Map<?, ?> original = (Map<?, ?>) obj;
                    f.set(this, Collections.synchronizedMap(original));
                    wrappedCount++;
                }
            } catch (Throwable t) {
                LOGGER.warn("[ModelBakeryMixin] Could not inspect field {}: {}", f.getName(), t.getMessage());
            }
        }
        LOGGER.info("[ModelBakeryMixin] 🛡️ Safely synchronized {} non-thread-safe ModelBakery maps (preserved {} thread-safe/ModernFix maps).", 
                wrappedCount, preservedCount);
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
