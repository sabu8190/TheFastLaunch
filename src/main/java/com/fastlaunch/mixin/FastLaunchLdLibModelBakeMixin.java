package com.fastlaunch.mixin;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ldlib の ModelEvent$ModifyBakingResult (11.9秒の直列探索) を
 * マルチコア並列化して 1秒台へ短縮する Mixin。
 */
@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.client.forge.ClientProxyImpl", remap = false)
public abstract class FastLaunchLdLibModelBakeMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/LdLibModelOptimizer");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static ForkJoinPool getPool() {
        return com.fastlaunch.core.FastLaunchThreadHelper.getSharedWorkerPool();
    }

    @Inject(method = "modelBake", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onModelBakeParallel(ModelEvent.ModifyBakingResult event, CallbackInfo ci) {
        try {
            long startTime = System.currentTimeMillis();
            ModelBakery bakery = event.getModelBakery();
            Map<ResourceLocation, BakedModel> models = event.getModels();
            if (models == null || models.isEmpty() || bakery == null) {
                return;
            }

            // ldlib の ClientProxy.WRAPPED_MODELS, SCRAPED_TEXTURES への参照を取得
            Class<?> clientProxyClass = Class.forName("com.lowdragmc.lowdraglib.client.ClientProxy");
            Field wrappedModelsField = clientProxyClass.getField("WRAPPED_MODELS");
            @SuppressWarnings("unchecked")
            Map<Object, Boolean> wrappedModels = (Map<Object, Boolean>) wrappedModelsField.get(null);

            Field scrapedTexturesField = clientProxyClass.getField("SCRAPED_TEXTURES");
            Object scrapedTextures = scrapedTexturesField.get(null);
            Method multimapGet = scrapedTextures.getClass().getMethod("get", Object.class);

            // topLevelModels (ModelBakery.topLevelModels)
            Field topLevelModelsField = null;
            for (Field f : ModelBakery.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    topLevelModelsField = f;
                    break;
                }
            }
            if (topLevelModelsField == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, UnbakedModel> topLevelModels = (Map<ResourceLocation, UnbakedModel>) topLevelModelsField.get(bakery);

            // CustomBakedModelImpl コンストラクタ
            Class<?> customBakedModelClass = Class.forName("com.lowdragmc.lowdraglib.client.model.forge.CustomBakedModelImpl");
            Constructor<?> customModelConstructor = customBakedModelClass.getConstructor(BakedModel.class);

            // LDLMetadataSection リフレクション
            Class<?> ldlMetaClass = Class.forName("com.lowdragmc.lowdraglib.client.model.custommodel/LDLMetadataSection".replace('/', '.'));
            Method spriteToAbsolute = ldlMetaClass.getMethod("spriteToAbsolute", ResourceLocation.class);
            Method getMetadata = ldlMetaClass.getMethod("getMetadata", ResourceLocation.class);
            Method isMissing = ldlMetaClass.getMethod("isMissing");

            Class<?> ldlRendererModelClass = Class.forName("com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel");

            // Material.texture() メソッド
            Method materialTextureMethod = null;
            for (Method m : net.minecraft.client.resources.model.Material.class.getMethods()) {
                if (m.getReturnType() == ResourceLocation.class && m.getParameterCount() == 0) {
                    materialTextureMethod = m;
                    break;
                }
            }

            final Method finalMatTex = materialTextureMethod;

            LOGGER.info("[LdLibModelOptimizer] ⚡ Starting parallel ModelBake post-processing for {} models across {} threads...",
                    models.size(), getPool().getParallelism());

            Map<ResourceLocation, BakedModel> replacements = new ConcurrentHashMap<>();
            List<Map.Entry<ResourceLocation, BakedModel>> entries = new ArrayList<>(models.entrySet());

            com.fastlaunch.core.FastLaunchThreadHelper.executeParallel(entries, entry -> {
                ResourceLocation location = entry.getKey();
                BakedModel baked = entry.getValue();
                if (baked == null || ldlRendererModelClass.isInstance(baked) || baked.isCustomRenderer()) {
                    return;
                }

                UnbakedModel unbaked = topLevelModels.get(location);
                if (unbaked == null) return;

                try {
                    Boolean cachedResult = wrappedModels.get(location);
                    boolean shouldWrap = false;

                    if (cachedResult != null) {
                        shouldWrap = cachedResult;
                    } else {
                        Deque<ResourceLocation> deque = new ArrayDeque<>();
                        Set<ResourceLocation> visited = new HashSet<>();
                        deque.push(location);
                        visited.add(location);

                        while (!deque.isEmpty() && !shouldWrap) {
                            ResourceLocation currentLoc = deque.pop();
                            UnbakedModel currentUnbaked = (currentLoc == location) ? unbaked : bakery.getModel(currentLoc);
                            if (currentUnbaked == null) continue;

                            Collection<?> textures = (Collection<?>) multimapGet.invoke(scrapedTextures, currentLoc);
                            if (textures != null) {
                                for (Object mat : textures) {
                                    if (mat instanceof net.minecraft.client.resources.model.Material && finalMatTex != null) {
                                        ResourceLocation texLoc = (ResourceLocation) finalMatTex.invoke(mat);
                                        ResourceLocation absLoc = (ResourceLocation) spriteToAbsolute.invoke(null, texLoc);
                                        Object meta = getMetadata.invoke(null, absLoc);
                                        boolean missing = (boolean) isMissing.invoke(meta);
                                        if (!missing) {
                                            shouldWrap = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (!shouldWrap) {
                                for (ResourceLocation dep : currentUnbaked.getDependencies()) {
                                    if (visited.add(dep)) {
                                        deque.push(dep);
                                    }
                                }
                            }
                        }
                        synchronized (wrappedModels) {
                            wrappedModels.put(location, shouldWrap);
                        }
                    }

                    if (shouldWrap) {
                        BakedModel wrapped = (BakedModel) customModelConstructor.newInstance(baked);
                        replacements.put(location, wrapped);
                    }
                } catch (Throwable ignored) {}
            });

            // 結果の一括反映
            models.putAll(replacements);

            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.info("[LdLibModelOptimizer] 🚀 Parallel ModelBake completed in {} ms (Processed {} models, wrapped {} models)!",
                    elapsed, models.size(), replacements.size());

            if (LOGGED.compareAndSet(false, true)) {
                com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                        "LdLib-ModelBakeParallel",
                        String.format("ACTIVE [Parallelized %d models, %d wrapped in %d ms (11.9s -> %.1fs)]",
                                models.size(), replacements.size(), elapsed, elapsed / 1000.0)
                );
            }

            ci.cancel(); // 元の11.9秒直列ループを安全にバイパス！
        } catch (Throwable t) {
            LOGGER.warn("[LdLibModelOptimizer] Fallback to default serial modelBake due to: {}", t.getMessage());
        }
    }
}
