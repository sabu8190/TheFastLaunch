package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchObjectHolderCacheEngine;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * ObjectHolderRegistry の走査と適用をインターセプトし、
 * O(1) レジストリインデックスキャッシュによる超高速バッチ適用を行う Mixin。
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.registries.ObjectHolderRegistry", remap = false)
public abstract class FastLaunchObjectHolderMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ObjectHolderOpt");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static long objectHolderStartTime = 0;

    @Inject(method = "findObjectHolders", at = @At("HEAD"), require = 0, remap = false)
    private static void onFindObjectHoldersHead(CallbackInfo ci) {
        objectHolderStartTime = System.currentTimeMillis();
    }

    @Inject(method = "findObjectHolders", at = @At("RETURN"), require = 0, remap = false)
    private static void onFindObjectHoldersReturn(CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            long elapsed = Math.max(0, System.currentTimeMillis() - objectHolderStartTime);
            LOGGER.info("[ObjectHolderProfiler] ⚡ ObjectHolderRegistry scan completed in {} ms.", elapsed);
            // 事前インデックス構築
            var holders = FastLaunchObjectHolderCacheEngine.getRawObjectHolders();
            if (holders != null) {
                FastLaunchObjectHolderCacheEngine.ensureIndexed(holders);
            }
            FastLaunchSuccessLogger.recordActiveFeature(
                    "ObjectHolderScan", 
                    String.format("ACTIVE [Scanned in %d ms, Indexed %d entries]", 
                            elapsed, holders != null ? holders.size() : 0)
            );
        }
    }

    /**
     * applyObjectHolders(Predicate) をインターセプトし、
     * 50レジストリ × 2万ハンドラの全件走査（100万回）を O(InjectedFields) に最適化
     */
    @Inject(method = "applyObjectHolders(Ljava/util/function/Predicate;)V", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void onApplyObjectHoldersHead(Predicate<ResourceLocation> filter, CallbackInfo ci) {
        if (FastLaunchObjectHolderCacheEngine.applyOptimized(filter)) {
            ci.cancel();
        }
    }
}
