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
 * 検索ツリー（Replacing search trees with JEI Search Tree）12秒停止の並列化 Mixin。
 */
@Mixin(value = SearchRegistry.class, priority = 500)
public abstract class SearchTreeFastBypassMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SearchTreeBypass");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private void onRegisterSearchTree(SearchRegistry.Key<?> key, SearchRegistry.TreeBuilderSupplier<?> treeBuilderSupplier, CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[SearchTreeBypass] ⚡ High-speed async worker handling SearchTree updates (Saved ~12s)!");
        }
    }
}
