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
            Field mainExecField = findField(clazz, "val$mainThreadExecutor", "f_10796_");
            Field listenerField = findField(clazz, "val$listener", "f_10797_");
            Field prevTaskField = findField(clazz, "val$previousTask", "f_10798_");
            Field thisField = findField(clazz, "this$0", "f_10795_");

            if (mainExecField == null || listenerField == null || prevTaskField == null || thisField == null) {
                return;
            }

            Executor mainThreadExecutor = (Executor) mainExecField.get(this);
            PreparableReloadListener listener = (PreparableReloadListener) listenerField.get(this);
            CompletableFuture<?> previousTask = (CompletableFuture<?>) prevTaskField.get(this);
            SimpleReloadInstance reloadInstance = (SimpleReloadInstance) thisField.get(this);

            if (mainThreadExecutor == null || listener == null || previousTask == null || reloadInstance == null) {
                return;
            }

            // バニラの進捗管理（preparingListeners の除外と全完了時の allPreparations.complete）を通常通りディスパッチ
            mainThreadExecutor.execute(() -> {
                try {
                    Field preparingField = findField(SimpleReloadInstance.class, "preparingListeners", "f_10793_");
                    if (preparingField != null) {
                        @SuppressWarnings("unchecked")
                        Set<PreparableReloadListener> set = (Set<PreparableReloadListener>) preparingField.get(reloadInstance);
                        if (set != null) {
                            set.remove(listener);
                            if (set.isEmpty()) {
                                Field allPrepField = findField(SimpleReloadInstance.class, "allPreparations", "f_10794_");
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
                } catch (Throwable ignored) {}
            });

            // ModelManager は全テクスチャ・アトラスの同期を待つ必要があるためバニラ動作（allPreparations 待ち）に任せる
            if (isModelDependent(listener)) {
                return;
            }

            // モデル非依存リスナーは、直前のリスナーの apply 完了に直接パイプライン結合して即時 apply を開始！
            int count = EARLY_APPLIED_COUNT.incrementAndGet();
            if (count % 30 == 0 || count == 1) {
                LOGGER.info("[ReloadBarrier] ⚡ Pipelined early apply active for independent listener #{} ({})",
                        count, listener.getName());
            }

            @SuppressWarnings("unchecked")
            CompletableFuture<T> earlyFuture = (CompletableFuture<T>) previousTask.thenApply(prev -> backgroundResult);
            cir.setReturnValue(earlyFuture);

            if (count == 100) {
                FastLaunchSuccessLogger.recordActiveFeature(
                        "PipelinedEarlyApply",
                        "ACTIVE [Pipelined early apply enabled for independent reload listeners]"
                );
            }
        } catch (Throwable t) {
            LOGGER.warn("[ReloadBarrier] Failed to pipeline listener, falling back to vanilla barrier: {}", t.getMessage());
        }
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

    private boolean isModelDependent(PreparableReloadListener listener) {
        if (listener == null) return false;
        String name = listener.getName().toLowerCase();
        String className = listener.getClass().getName().toLowerCase();
        return name.contains("model") || className.contains("modelmanager") || className.contains("modelbakery");
    }
}
