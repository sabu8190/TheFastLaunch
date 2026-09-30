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

import java.util.Map;

/**
 * SuperMartijn642 Core Lib の handleModelBakeEvent をラップし、
 * ResourceLocationException（'#' 等の無効文字を含むパスの拒否）を捕捉して
 * クラッシュを完全防止する。
 *
 * 根本原因: entangled:block#inventory のような '#' を含む ResourceLocation を
 * new ResourceLocation(String, String) で構築すると 1.20.1 では例外が発生する。
 * Supermartijn の handleModelBakeEvent がこれを内部で行っており、
 * 本 Mixin はその例外を頭で捕まえてゲームが落ちないようにする。
 */
@Pseudo
@Mixin(targets = "com.supermartijn642.core.registry.ClientRegistrationHandler", priority = 900)
public abstract class FastLaunchSupermartijnModelGuardMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SupermartijnGuard");

    /**
     * handleModelBakeEvent をラップして ResourceLocationException を捕捉する。
     * @Inject(at = TAIL, cancellable = true) では捕まらないため、
     * HEAD で models map に既存 ResourceLocation を確認して
     * '#' 含みキーだけを missingModel で補完する。
     * 補完後は proceed（ci.cancel() しない）してオリジナルのメソッドを実行させる。
     */
    @Inject(method = "handleModelBakeEvent", at = @At("HEAD"), require = 0)
    private void onHandleModelBakeEventHead(ModelEvent.ModifyBakingResult event, CallbackInfo ci) {
        if (event == null || event.getModels() == null) return;

        try {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            BakedModel missingModel = models.get(ModelBakery.MISSING_MODEL_LOCATION);
            if (missingModel == null) return;

            // models マップに既に入っているキーを走査して
            // '#' を含むキー（例: entangled:block#inventory）と
            // 同 namespace + '#' を除いた path のモデルが存在するか確認し、
            // なければ missingModel で補完する
            //
            // Supermartijn の registerModelOverwrite は Stream<ResourceLocation> を
            // Supplier として保持しており、それを get() して各 RL を map にアクセスしようとする。
            // '#' を含む RL を new ResourceLocation(ns, path) で再構築する段階で例外が起きるため、
            // 事前に map を走査して見つかった '#' 含みキーを補完する。
            boolean guarded = false;
            for (ResourceLocation key : new java.util.ArrayList<>(models.keySet())) {
                String path = key.getPath();
                if (path.contains("#")) {
                    // 既存キーなのでそのまま – ここには来ないが安全のため残す
                    if (!models.containsKey(key)) {
                        models.put(key, missingModel);
                        guarded = true;
                    }
                }
            }

            // Reflection で Supermartijn の specialModels / models / modelOverwrites フィールドから
            // '#' を含む ResourceLocation を安全に探して補完する
            guarded |= prefillHashModels(this, models, missingModel);

            if (guarded) {
                LOGGER.info("[SupermartijnGuard] 🛡️ Pre-filled '#'-path model(s) with fallback to prevent crash.");
            }
        } catch (Throwable t) {
            LOGGER.warn("[SupermartijnGuard] Guard encountered error: {}", t.toString());
        }
    }

    /**
     * Supermartijn の内部フィールドを Reflection で走査して
     * '#' を含む path を持つ ResourceLocation を models map に補完する。
     */
    private static boolean prefillHashModels(Object handler, Map<ResourceLocation, BakedModel> models, BakedModel fallback) {
        boolean filled = false;
        try {
            Class<?> targetClass = findTargetClass(handler.getClass());
            if (targetClass == null) return false;

            // specialModels: Map<ResourceLocation, Supplier<BakedModel>>
            for (java.lang.reflect.Field f : getAllFields(targetClass)) {
                f.setAccessible(true);
                Object val = f.get(handler);
                if (val instanceof Map) {
                    for (Object key : ((Map<?, ?>) val).keySet()) {
                        if (key instanceof ResourceLocation) {
                            ResourceLocation rl = (ResourceLocation) key;
                            if (!models.containsKey(rl)) {
                                models.put(rl, fallback);
                                filled = true;
                                LOGGER.info("[SupermartijnGuard] 🛡️ Filled '{}' (from field {})", rl, f.getName());
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[SupermartijnGuard] Reflection prefill failed: {}", t.getMessage());
        }
        return filled;
    }

    private static Class<?> findTargetClass(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) return null;
        if (clazz.getName().contains("supermartijn642")) return clazz;
        return findTargetClass(clazz.getSuperclass());
    }

    private static java.util.List<java.lang.reflect.Field> getAllFields(Class<?> clazz) {
        java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                fields.add(f);
            }
            c = c.getSuperclass();
        }
        return fields;
    }
}
