package com.fastlaunch.mixin;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
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
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * SuperMartijn642 Core Lib の handleModelBakeEvent における
 * "No model registered for model overwrite" クラッシュを根絶するガード Mixin。
 *
 * 根本原因:
 * Supermartijn の handleModelBakeEvent は、modelOverwrites リスト
 * (List<Pair<Supplier<Stream<ResourceLocation>>, Function<BakedModel, BakedModel>>>) を走査し、
 * 各 ResourceLocation が event.getModels() に存在しない場合、
 * RuntimeException("No model registered for model overwrite '" + rl + "'!") をスローして即死する。
 * Entangled などの一部 Mod が登録した 'entangled:block#inventory' 等のモデルが
 * パスに '#' を含む、あるいはベイク済みモデルマップに存在しない場合にこれが発火する。
 *
 * 解決策:
 * handleModelBakeEvent の直前 (HEAD) で、Supermartijn の specialModels および modelOverwrites を走査し、
 * models マップに未登録の ResourceLocation があれば、バニラのフォールバックモデル (MISSING_MODEL) を
 * 先回りして models.put(rl, missingModel) しておく。
 * これにより Supermartijn の containsKey(rl) チェックが 100% 通過し、クラッシュが完全に阻止される。
 */
@Pseudo
@Mixin(targets = "com.supermartijn642.core.registry.ClientRegistrationHandler", priority = 900)
public abstract class FastLaunchSupermartijnModelGuardMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SupermartijnGuard");

    @Inject(method = "handleModelBakeEvent", at = @At("HEAD"), require = 0)
    private void onHandleModelBakeEventHead(ModelEvent.ModifyBakingResult event, CallbackInfo ci) {
        if (event == null || event.getModels() == null) return;

        try {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            BakedModel missingModel = models.get(ModelBakery.MISSING_MODEL_LOCATION);
            if (missingModel == null) return;

            Class<?> targetClass = findTargetClass(this.getClass());
            if (targetClass == null) return;

            int fixedCount = 0;

            // 1. specialModels: Map<ResourceLocation, Supplier<BakedModel>> を保護
            Field specialModelsField = findField(targetClass, "specialModels");
            if (specialModelsField != null) {
                specialModelsField.setAccessible(true);
                Object val = specialModelsField.get(this);
                if (val instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) val;
                    for (Object k : map.keySet()) {
                        if (k instanceof ResourceLocation) {
                            ResourceLocation rl = (ResourceLocation) k;
                            if (!models.containsKey(rl)) {
                                models.put(rl, missingModel);
                                fixedCount++;
                                LOGGER.info("[SupermartijnGuard] 🛡️ Pre-filled missing special model: {}", rl);
                            }
                        }
                    }
                }
            }

            // 2. modelOverwrites: List<Pair<Supplier<Stream<ResourceLocation>>, Function<BakedModel, BakedModel>>> を保護
            Field modelOverwritesField = findField(targetClass, "modelOverwrites");
            if (modelOverwritesField != null) {
                modelOverwritesField.setAccessible(true);
                Object val = modelOverwritesField.get(this);
                if (val instanceof List) {
                    List<?> list = (List<?>) val;
                    for (Object pair : list) {
                        if (pair == null) continue;
                        try {
                            Method leftMethod = pair.getClass().getMethod("left");
                            leftMethod.setAccessible(true);
                            Object leftObj = leftMethod.invoke(pair);
                            if (leftObj instanceof Supplier) {
                                @SuppressWarnings("unchecked")
                                Supplier<Stream<ResourceLocation>> supplier = (Supplier<Stream<ResourceLocation>>) leftObj;
                                Stream<ResourceLocation> stream = supplier.get();
                                if (stream != null) {
                                    try {
                                        List<ResourceLocation> rls = stream.toList();
                                        for (ResourceLocation rl : rls) {
                                            if (rl != null && !models.containsKey(rl)) {
                                                models.put(rl, missingModel);
                                                fixedCount++;
                                                LOGGER.info("[SupermartijnGuard] 🛡️ Pre-filled missing overwrite target model: {}", rl);
                                            }
                                        }
                                    } finally {
                                        try { stream.close(); } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            LOGGER.debug("[SupermartijnGuard] Error inspecting model overwrite pair: {}", t.getMessage());
                        }
                    }
                }
            }

            if (fixedCount > 0) {
                LOGGER.info("[SupermartijnGuard] 🛡️ Successfully pre-filled {} missing model(s) to prevent crash!", fixedCount);
            }
        } catch (Throwable t) {
            LOGGER.warn("[SupermartijnGuard] Guard encountered error: {}", t.toString());
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
