package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchThreadHelper;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.client.renderer.texture.SpriteLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * SpriteLoader 多段並列化＆テクスチャステッチ・Mipmap加速 Mixin。
 * 数千枚のスプライト Mipmap 生成 (increaseMipLevel) および画像サプライヤ読み込みを
 * FastLaunch Quiet Worker Pool (Pコア優先) で完全並列化。
 */
@Mixin(value = SpriteLoader.class, priority = 500)
public abstract class FastLaunchSpriteLoaderMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SpriteLoader");

    /**
     * SpriteLoader.loadAndStitch の背景 Executor を FastLaunch 共有ワーカープールに統一。
     * PNG 画像デコード（runSpriteSuppliers）が P コア優先マルチスレッドで並列実行される。
     */
    @ModifyVariable(
            method = "loadAndStitch",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private Executor onModifyLoadAndStitchExecutor(Executor executor) {
        return FastLaunchThreadHelper.getSharedWorkerPool();
    }

    /**
     * SpriteLoader.stitch の Mipmap 非同期ディスパッチ Executor を FastLaunch 共有プールに統一。
     */
    @Redirect(
            method = "stitch",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            ),
            require = 0
    )
    private CompletableFuture<Void> onRedirectRunAsyncMipmap(Runnable runnable, Executor executor) {
        long start = System.currentTimeMillis();
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
                long elapsed = Math.max(0, System.currentTimeMillis() - start);
                LOGGER.info("[SpriteLoader] 🎨 SpriteLoader Mipmap async generation completed in {} ms.", elapsed);
                FastLaunchSuccessLogger.recordActiveFeature(
                        "SpriteLoader-MipmapGeneration",
                        String.format("ACTIVE [SpriteLoader Mipmap generation completed in %d ms]", elapsed)
                );
            } catch (Throwable t) {
                LOGGER.warn("[SpriteLoader] Error during Mipmap generation: {}", t.getMessage());
            }
        }, FastLaunchThreadHelper.getSharedWorkerPool());
    }
}
