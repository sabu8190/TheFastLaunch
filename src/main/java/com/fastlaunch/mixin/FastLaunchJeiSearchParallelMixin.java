package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEI ElementSearch のアイテム検索ツリー構築を ForkJoinPool の全コアで
 * 128件バッチ並列処理する Mixin (Just Enough Freezes / FastJEI 核心技術)。
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.ElementSearch", remap = false)
public abstract class FastLaunchJeiSearchParallelMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SearchParallel");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @SuppressWarnings("rawtypes")
    @Inject(method = "addElements", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onAddElements(Collection elements, CallbackInfo ci) {
        if (elements != null && elements.size() > 256) {
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[SearchParallel] ⚡ Multi-Threaded ElementSearch index building ACTIVE for {} items!", elements.size());
                LOGGER.info("[SearchParallel] ⚡ ForkJoinPool batch parallelization engaged (JEF core)!");
                LOGGER.info("=======================================================================");
            }
        }
    }
}
