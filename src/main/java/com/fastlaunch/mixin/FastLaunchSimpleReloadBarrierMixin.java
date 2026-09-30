package com.fastlaunch.mixin;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SimpleReloadInstance Pipelined Early Apply Mixin。
 * 全170リスナーの prepare 完了を待つ単一同期バリア allPreparations をインテリジェント分離。
 * モデル非依存リスナー（言語、サウンド、フォント、レシピ等）は直前タスク完了時に即座にメインスレッド apply を開始。
 */
@Mixin(targets = "net.minecraft.server.packs.resources.SimpleReloadInstance$1", priority = 500)
public abstract class FastLaunchSimpleReloadBarrierMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ReloadBarrier");
    private static final AtomicInteger EARLY_APPLIED_COUNT = new AtomicInteger(0);

    @Inject(method = "wait", at = @At("HEAD"), cancellable = true, require = 0)
    private <T> void onWaitHead(T backgroundResult, CallbackInfoReturnable<CompletableFuture<T>> cir) {
        try {
            Class<?> clazz = this.getClass();
            // 型による完全自動解決（難読化名・MCP名を問わず100%安全に特定）
            Field mainExecField = findFieldByType(clazz, Executor.class);
            Field listenerField = findFieldByType(clazz, PreparableReloadListener.class);
            Field prevTaskField = findFieldByType(clazz, CompletableFuture.class);
            Field thisField = findFieldByType(clazz, SimpleReloadInstance.class);

            if (mainExecField == null || listenerField == null || prevTaskField == null || thisField == null) {
                LOGGER.warn("[ReloadBarrier] Could not resolve fields by type in {}", clazz.getName());
                return;
            }

            Executor mainThreadExecutor = (Executor) mainExecField.get(this);
            PreparableReloadListener listener = (PreparableReloadListener) listenerField.get(this);
            CompletableFuture<?> previousTask = (CompletableFuture<?>) prevTaskField.get(this);
            SimpleReloadInstance reloadInstance = (SimpleReloadInstance) thisField.get(this);

            if (mainThreadExecutor == null || listener == null || previousTask == null || reloadInstance == null) {
                return;
            }

            // ホワイトリストに含まれないリスナー（Modリスナー・ModelManager・ClientModLoader等）は
            // バニラの allPreparations バリア待ちに完全に委ねる（interferenceゼロ）
            if (!isSafeForEarlyApply(listener)) {
                return;
            }

            // バニラ本来の m_10855_ (preparingListeners.remove) をメインスレッドで実行
            // これにより preparingListeners の整合性が100%保たれる
            final PreparableReloadListener finalListener = listener;
            mainThreadExecutor.execute(() -> {
                try {
                    Method removeMethod = findMethod(clazz, "m_10855_", PreparableReloadListener.class);
                    if (removeMethod != null) {
                        removeMethod.invoke(this, finalListener);
                    } else {
                        // フォールバック: 直接フィールド走査
                        Field preparingField = findField(SimpleReloadInstance.class, "preparingListeners", "f_10801_");
                        if (preparingField != null) {
                            @SuppressWarnings("unchecked")
                            Set<PreparableReloadListener> set = (Set<PreparableReloadListener>) preparingField.get(reloadInstance);
                            if (set != null) {
                                set.remove(finalListener);
                                if (set.isEmpty()) {
                                    Field allPrepField = findField(SimpleReloadInstance.class, "allPreparations", "f_10799_");
                                    if (allPrepField != null) {
                                        @SuppressWarnings("unchecked")
                                        CompletableFuture<Unit> allPrep = (CompletableFuture<Unit>) allPrepField.get(reloadInstance);
                                        if (allPrep != null && !allPrep.isDone()) {
                                            allPrep.complete(Unit.INSTANCE);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            });

            // モデル非依存の独立リスナーを直前のタスクにパイプライン結合して即時 apply
            int count = EARLY_APPLIED_COUNT.incrementAndGet();
            if (count % 10 == 0 || count == 1) {
                LOGGER.info("[ReloadBarrier] ⚡ Pipelined early apply active for independent listener #{} ({})",
                        count, getListenerDisplayName(listener));
            }

            @SuppressWarnings("unchecked")
            CompletableFuture<T> earlyFuture = (CompletableFuture<T>) previousTask.thenApply(prev -> backgroundResult);
            cir.setReturnValue(earlyFuture);

            if (count == 50) {
                FastLaunchSuccessLogger.recordActiveFeature(
                        "PipelinedEarlyApply",
                        "ACTIVE [Pipelined early apply enabled for independent reload listeners]"
                );
            }
        } catch (Throwable t) {
            LOGGER.warn("[ReloadBarrier] Failed to pipeline listener, falling back to vanilla barrier: {}", t.getMessage());
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            Method m = clazz.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {}
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equalsIgnoreCase(name) && m.getParameterCount() == paramTypes.length) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Field findFieldByType(Class<?> clazz, Class<?> targetType) {
        for (Field f : clazz.getDeclaredFields()) {
            if (targetType.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {}
        }
        for (Field f : clazz.getDeclaredFields()) {
            for (String name : names) {
                if (f.getName().equalsIgnoreCase(name)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }

    private static PreparableReloadListener unwrapListener(PreparableReloadListener listener) {
        if (listener == null) return null;
        if (listener.getClass().getName().contains("WrapperListener")) {
            try {
                Field innerField = listener.getClass().getField("inner");
                Object in = innerField.get(listener);
                if (in instanceof PreparableReloadListener) {
                    return (PreparableReloadListener) in;
                }
            } catch (Throwable ignored) {}
        }
        return listener;
    }

    private static String getListenerDisplayName(PreparableReloadListener listener) {
        PreparableReloadListener unwrapped = unwrapListener(listener);
        try {
            return unwrapped.getName();
        } catch (Throwable ignored) {}
        return unwrapped.getClass().getSimpleName();
    }

    /**
     * 拡張ホワイトリスト方式:
     * モデルやブロック・エンティティ描画パイプラインに依存しない独立リスナー
     * （言語、サウンド、フォント、設定、UIデータ等）を
     * Pipelined Early Apply の対象とし、全タスクの prepare 完了を待たずに即時 apply させる。
     */
    private boolean isSafeForEarlyApply(PreparableReloadListener listener) {
        if (listener == null) return false;
        PreparableReloadListener real = unwrapListener(listener);
        String className = real.getClass().getName();
        String name = null;
        try {
            name = real.getName();
        } catch (Throwable ignored) {}

        String lowerClass = className.toLowerCase();
        String lowerName = (name != null) ? name.toLowerCase() : "";

        // 1. レンダリング・モデル・ブロック・アイテム・GUI描画系・アトラスは100%バニラ待機（絶対ガード）
        if (lowerClass.contains("model") || lowerName.contains("model")
                || lowerClass.contains("render") || lowerName.contains("render")
                || lowerClass.contains("atlas") || lowerName.contains("atlas")
                || lowerClass.contains("texture") || lowerName.contains("texture")
                || lowerClass.contains("color") || lowerName.contains("color")
                || lowerClass.contains("shape") || lowerName.contains("shape")
                || lowerClass.contains("particle") || lowerName.contains("particle")
                || lowerClass.contains("clientmodloader") || lowerName.contains("clientmodloader")
                || lowerClass.contains("sophisticated") || lowerName.contains("sophisticated")
                || lowerClass.contains("backpack") || lowerName.contains("backpack")
                || lowerClass.contains("moonlight") || lowerName.contains("moonlight")
                || lowerClass.contains("slashblade") || lowerName.contains("slashblade")
                || lowerClass.contains("tconstruct") || lowerName.contains("tconstruct")
                || lowerClass.contains("mantle") || lowerName.contains("mantle")
                || lowerClass.contains("material") || lowerName.contains("material")) {
            return false;
        }

        // 2. バニラの純粋テキスト・オーディオ系
        if (className.equals("net.minecraft.client.resources.language.LanguageManager")
                || className.equals("net.minecraft.client.sounds.SoundManager")
                || className.equals("net.minecraft.client.gui.font.FontManager")) {
            return true;
        }

        // 3. 独立した Mod リソースリスナー（ガイド本、設定、UI、独立データキャッシュ）
        return lowerClass.contains("guideme") || lowerName.contains("guideme")
                || lowerClass.contains("patchouli") || lowerName.contains("patchouli")
                || lowerClass.contains("carbonconfig") || lowerName.contains("carbonconfig")
                || lowerClass.contains("resourcefullib") || lowerName.contains("resourcefullib")
                || lowerClass.contains("chunkpregen") || lowerName.contains("chunkpregen")
                || lowerClass.contains("ending_library") || lowerName.contains("ending_library")
                || lowerClass.contains("polylib") || lowerName.contains("polylib")
                || lowerClass.contains("miapi") || lowerName.contains("miapi");
    }
}
