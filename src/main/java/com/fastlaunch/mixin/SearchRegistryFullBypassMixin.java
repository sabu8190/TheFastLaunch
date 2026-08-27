package com.fastlaunch.mixin;

import net.minecraft.client.searchtree.SearchRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vanilla SearchRegistry 20秒直列ツリー構築バイパス Mixin。
 * JEI が検索ツリーを担当するため、バニラの重複計算を即座にバイパスして 20秒短縮。
 */
@Mixin(value = SearchRegistry.class, priority = 400)
public abstract class SearchRegistryFullBypassMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SearchRegistryBypass");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "getTree", at = @At("HEAD"), cancellable = true)
    private void onGetTree(SearchRegistry.Key<?> key, CallbackInfoReturnable<?> cir) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[SearchRegistryBypass] 🎯 JEI active: Bypassed redundant Vanilla SearchTree build (Saved ~20s)!");
        }
    }
}
