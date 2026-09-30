package com.fastlaunch.mixin;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * SuperMartijn642 Core Lib の handleModelBakeEvent における
 * "No model registered for model overwrite" クラッシュを根絶する完全防御 Mixin。
 *
 * 根本原因:
 * Supermartijn の handleModelBakeEvent は、modelOverwrites リストを走査し、
 * 各 ResourceLocation が event.getModels() に存在しない場合、
 * RuntimeException("No model registered for model overwrite '" + rl + "'!") をスローして即死する。
 * Entangled などの一部 Mod が登録した 'entangled:block#inventory' 等のモデルが
 * 存在しない場合にこれが発火する。
 *
 * 解決策:
 * handleModelBakeEvent の HEAD で元メソッドをキャンセル (ci.cancel()) し、
 * 安全な上書き処理を自前で実行する。
 * 未登録モデルが存在しても、例外をスローせず安全にスキップすることでゲーム全体のクラッシュを 100% 根絶する。
 */
@Pseudo
@Mixin(targets = "com.supermartijn642.core.registry.ClientRegistrationHandler", priority = 900)
public abstract class FastLaunchSupermartijnModelGuardMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SupermartijnGuard");

    @Inject(method = "handleModelBakeEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void onHandleModelBakeEvent(ModelEvent.ModifyBakingResult event, CallbackInfo ci) {
        if (event == null || event.getModels() == null) return;

        // 元の危険な例外スロー処理を完全にキャンセル
        ci.cancel();

        try {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            Class<?> targetClass = findTargetClass(this.getClass());
            if (targetClass == null) {
                LOGGER.warn("[SupermartijnGuard] Could not resolve ClientRegistrationHandler class!");
                return;
            }

            // 1. specialModels: Map<ResourceLocation, Supplier<BakedModel>> を安全に適用
            Field specialModelsField = findField(targetClass, "specialModels");
            if (specialModelsField != null) {
                specialModelsField.setAccessible(true);
                Object val = specialModelsField.get(this);
                if (val instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) val;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        Object k = entry.getKey();
                        Object v = entry.getValue();
                        if (k instanceof ResourceLocation && v instanceof Supplier) {
                            ResourceLocation rl = (ResourceLocation) k;
                            if (models.containsKey(rl)) {
                                LOGGER.debug("[SupermartijnGuard] Duplicate model registration for '{}', skipping.", rl);
                                continue;
                            }
                            try {
                                @SuppressWarnings("unchecked")
                                Supplier<BakedModel> supplier = (Supplier<BakedModel>) v;
                                BakedModel model = supplier.get();
                                if (model != null) {
                                    models.put(rl, model);
                                }
                            } catch (Throwable t) {
                                LOGGER.warn("[SupermartijnGuard] Error providing special model for '{}': {}", rl, t.getMessage());
                            }
                        }
                    }
                }
            }

            // 2. modelOverwrites: List<Pair<Supplier<Stream<ResourceLocation>>, Function<BakedModel, BakedModel>>> を安全に適用
            Field modelOverwritesField = findField(targetClass, "modelOverwrites");
            if (modelOverwritesField != null) {
                modelOverwritesField.setAccessible(true);
                Object val = modelOverwritesField.get(this);
                if (val instanceof List) {
                    List<?> list = (List<?>) val;
                    int appliedCount = 0;
                    int skippedCount = 0;

                    for (Object pair : list) {
                        if (pair == null) continue;
                        try {
                            Method leftMethod = pair.getClass().getMethod("left");
                            Method rightMethod = pair.getClass().getMethod("right");
                            leftMethod.setAccessible(true);
                            rightMethod.setAccessible(true);

                            Object leftObj = leftMethod.invoke(pair);
                            Object rightObj = rightMethod.invoke(pair);

                            if (leftObj instanceof Supplier && rightObj instanceof Function) {
                                @SuppressWarnings("unchecked")
                                Supplier<Stream<ResourceLocation>> supplier = (Supplier<Stream<ResourceLocation>>) leftObj;
                                @SuppressWarnings("unchecked")
                                Function<BakedModel, BakedModel> function = (Function<BakedModel, BakedModel>) rightObj;

                                Stream<ResourceLocation> stream = supplier.get();
                                if (stream != null) {
                                    try {
                                        List<ResourceLocation> locations = stream.toList();
                                        for (ResourceLocation location : locations) {
                                            if (location == null) continue;

                                            BakedModel model = models.get(location);
                                            if (model == null) {
                                                // 元コードはここで RuntimeException を投げて即死！
                                                // 安全版では例外を投げずにスキップし、クラッシュを完全阻止！
                                                LOGGER.info("[SupermartijnGuard] 🛡️ Model overwrite target '{}' not registered in bake models, safely skipped!", location);
                                                skippedCount++;
                                                continue;
                                            }

                                            try {
                                                BakedModel overwritten = function.apply(model);
                                                if (overwritten != null) {
                                                    models.put(location, overwritten);
                                                    appliedCount++;
                                                }
                                            } catch (Throwable t) {
                                                LOGGER.warn("[SupermartijnGuard] Error applying model overwrite function for '{}': {}", location, t.getMessage());
                                            }
                                        }
                                    } finally {
                                        try { stream.close(); } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            LOGGER.debug("[SupermartijnGuard] Error processing model overwrite pair: {}", t.getMessage());
                        }
                    }

                    LOGGER.info("[SupermartijnGuard] 🛡️ Model overwrites finished: {} applied, {} missing targets safely skipped (Crash prevented!)", appliedCount, skippedCount);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[SupermartijnGuard] Critical error during safe handleModelBakeEvent execution", t);
        }
    }

    private static Class<?> findTargetClass(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return null;
        if (clazz.getName().contains("supermartijn642")) return clazz;
        return findTargetClass(clazz.getSuperclass());
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }
}
