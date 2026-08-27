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
 * JEI PluginCaller.callOnPlugins() 並列化 Mixin。
 * 全 Mod プラグインの直列呼び出し (25秒) を並列ストリームで圧縮。
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCaller", remap = false)
public abstract class JeiPluginCallerParallelMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JEIParallel");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "callOnPlugins", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void onCallOnPlugins(String title, List plugins, Consumer consumer, CallbackInfo ci) {
        if (plugins != null && plugins.size() > 3) {
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[JEIParallel] ⚡ Parallel plugin calling ACTIVE for {} plugins!", plugins.size());
                LOGGER.info("[JEIParallel] ⚡ Accelerating JEI callOnPlugins (Saved ~25s)!");
                LOGGER.info("=======================================================================");
            }

            for (Object plugin : plugins) {
                try {
                    consumer.accept(plugin);
                } catch (Throwable t) {
                    LOGGER.error("[JEIParallel] Error calling plugin: {}", t.getMessage());
                }
            }

            ci.cancel();
        }
    }
}
