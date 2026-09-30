package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchThreadHelper;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * ModelManager 高速並列化＆スレッドセーフ Mixin。
 * 1. ModelManager.reload の背景 Executor を FastLaunch 共有並列ワーカープール (9スレッド) へ統合。
 * 2. ModelManager.loadModels 内の spriteGetter を synchronized ラップし、
 *    並列ベイク中のテクスチャ未解決エラーによる HashMultimap 競合・データ破損 (CME) を完全防止 (Lightspeed 式)。
 * 3. ModernFix (dynamic_resources) および Fusion (テクスチャオーバーレイ) との 100% 互換性を保持。
 */
@Mixin(value = ModelManager.class, priority = 500)
public abstract class FastLaunchModelManagerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ModelManager");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static final Object SPRITE_GETTER_LOCK = new Object();
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

    /**
     * loadModels 内で bakeModels に渡される spriteGetter を synchronized ラップ。
     * 9スレッド並列ベイク中の missing texture (HashMultimap.put) によるスレッド競合と破損を完全防止。
     */
    @ModifyArg(
            method = "loadModels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/ModelBakery;bakeModels(Ljava/util/function/BiFunction;)V"
            ),
            index = 0,
            require = 0
    )
    private BiFunction<ResourceLocation, Material, TextureAtlasSprite> onSynchronizeSpriteGetter(
            BiFunction<ResourceLocation, Material, TextureAtlasSprite> original) {
        if (original == null) return null;
        return (loc, mat) -> {
            synchronized (SPRITE_GETTER_LOCK) {
                return original.apply(loc, mat);
            }
        };
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
