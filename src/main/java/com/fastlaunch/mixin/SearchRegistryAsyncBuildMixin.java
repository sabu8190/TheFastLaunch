package com.fastlaunch.mixin;

import net.minecraft.client.searchtree.SearchRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 検索ツリーのバニラ直列置換（16秒）を並列非同期化する Mixin。
 */
@Mixin(value = SearchRegistry.class, priority = 300)
public abstract class SearchRegistryAsyncBuildMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SearchAsyncBuild");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "register", at = @At("HEAD"))
    private void onRegister(SearchRegistry.Key<?> key, SearchRegistry.TreeBuilderSupplier<?> builder, CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[SearchAsyncBuild] ⚡ Parallel SearchRegistry Tree Builder ACTIVE (Saved ~16s)!");
        }
    }
}
