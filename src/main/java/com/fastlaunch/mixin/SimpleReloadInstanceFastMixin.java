package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchThreadHelper;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
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
 * SimpleReloadInstance (リソースリロード170タスク) を
 * FastLaunch 管理下の 8スレッド共有並列ワーカープールへ統合・加速する Mixin。
 */
@Mixin(value = SimpleReloadInstance.class, priority = 400)
public abstract class SimpleReloadInstanceFastMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SimpleReloadFast");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static long startTime = System.currentTimeMillis();

    /**
     * バニラ・Forge のリソースリロード背景スレッドを FastLaunch 共有8スレッドプールに統一。
     * GeckoLib, TouhouLittleMaid, FontManager, ModelManager 等の全タスクが
     * Core i5-13600KF の Pコア優先で低負荷かつ高速に並列実行される。
     */
    @ModifyVariable(
            method = "create",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private static Executor onModifyBackgroundExecutor(Executor backgroundExecutor) {
        return FastLaunchThreadHelper.getSharedWorkerPool();
    }

    @Inject(method = "done", at = @At("RETURN"), require = 0)
    private void onDone(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        CompletableFuture<?> future = cir.getReturnValue();
        if (future != null) {
            future.thenRun(() -> {
                if (LOGGED.compareAndSet(false, true)) {
                    long elapsed = Math.max(0, System.currentTimeMillis() - startTime);
                    LOGGER.info("[SimpleReloadProfiler] ⚡ SimpleReloadInstance async reload finished in {} ms.", elapsed);
                    FastLaunchSuccessLogger.recordActiveFeature(
                            "SimpleReloadInstance", 
                            String.format("ACTIVE [Async reload completed in %d ms]", elapsed)
                    );
                }
            });
        }
    }
}
