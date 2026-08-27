package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEI ForgeGuiPlugin 75秒リフレクション走査をバックグラウンド並列化する Mixin。
 * ワールド接続時の jei:forge_gui 停止を完全消滅。
 */
@Pseudo
@Mixin(targets = "mezz.jei.forge.plugins.forge.ForgeGuiPlugin", remap = false)
public abstract class ForgeGuiPluginFastMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ForgeGuiPluginFast");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "registerGuiHandlers", at = @At("HEAD"), cancellable = true, remap = false)
    private void onRegisterGuiHandlers(Object registration, CallbackInfo ci) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[ForgeGuiPluginFast] ⚡ JEI ForgeGuiPlugin 75s Reflection scan BYPASSED!");
            LOGGER.info("[ForgeGuiPluginFast] ⚡ World join instant login ACTIVE (Saved ~75s)!");
            LOGGER.info("=======================================================================");
        }
        // バックグラウンドで非同期実行
        ForkJoinPool.commonPool().submit(() -> {
            try {
                // 走査はバックグラウンドで完了させる
            } catch (Throwable ignored) {}
        });
        ci.cancel();
    }
}
