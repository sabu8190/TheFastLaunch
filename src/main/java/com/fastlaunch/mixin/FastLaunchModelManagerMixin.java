package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchThreadHelper;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.client.resources.model.ModelManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ModelManager 高速並列化 Mixin。
 * loadBlockModels / loadBlockStates / AtlasSet.scheduleLoad の全背景タスクを
 * FastLaunch 共有並列ワーカープール (Pコア優先) へ統合。
 */
@Mixin(value = ModelManager.class, priority = 500)
public abstract class FastLaunchModelManagerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ModelManager");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static long startTime = 0;

    /**
     * ModelManager.reload の背景 Executor を FastLaunch 共有並列プールへ統一。
     */
    @ModifyVariable(
            method = "reload",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private Executor onModifyModelManagerBackgroundExecutor(Executor backgroundExecutor) {
        startTime = System.currentTimeMillis();
        LOGGER.info("[ModelManager] 🚀 Redirecting ModelManager reload pipeline (block models, blockstates, atlases) to FastLaunch Quiet Worker Pool...");
        return FastLaunchThreadHelper.getSharedWorkerPool();
    }

    @Inject(method = "reload", at = @At("RETURN"), require = 0)
    private void onReloadReturn(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        CompletableFuture<Void> future = cir.getReturnValue();
        if (future != null) {
            future.thenRun(() -> {
                if (LOGGED.compareAndSet(false, true)) {
                    long elapsed = Math.max(0, System.currentTimeMillis() - startTime);
                    LOGGER.info("[ModelManager] 🎯 ModelManager async reload pipeline completed in {} ms.", elapsed);
                    FastLaunchSuccessLogger.recordActiveFeature(
                            "ModelManager-AsyncPipeline",
                            String.format("ACTIVE [ModelManager reload completed in %d ms]", elapsed)
                    );
                }
            });
        }
    }
}
