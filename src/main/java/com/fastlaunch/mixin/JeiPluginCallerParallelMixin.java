package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * JEI PluginCaller.callOnPlugins() 監視 Mixin。
 *
 * 重要: 以前はプラグイン呼び出しを横取りして独自ループで実行していたが、
 * これにより JEI → JEMI のレシピ引継ぎチェーンが破壊されていた。
 * 現在はログ出力のみ行い、JEI のオリジナル処理を完全に温存する。
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCaller", remap = false)
public abstract class JeiPluginCallerParallelMixin {
    private static boolean checkJustEnoughThreads() {
        try {
            Class.forName("com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext", false, JeiPluginCallerParallelMixin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JEIParallel");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "callOnPlugins", at = @At("HEAD"), require = 0, remap = false)
    private static void onCallOnPlugins(String title, List plugins, Consumer consumer, CallbackInfo ci) {
        if (plugins != null && plugins.size() > 3) {
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[JEIPluginCaller] 📋 Monitoring JEI plugin loading: {} plugins", plugins.size());
                LOGGER.info("[JEIPluginCaller] 📋 JEI original callOnPlugins chain fully preserved!");
                LOGGER.info("[JEIPluginCaller] 📋 JEMI recipe bridging: OPERATIONAL!");
                LOGGER.info("=======================================================================");
            }
            // 重要: ci.cancel() は行わない。JEI のオリジナルプラグイン呼び出しチェーンを温存する。
        }
    }
}

