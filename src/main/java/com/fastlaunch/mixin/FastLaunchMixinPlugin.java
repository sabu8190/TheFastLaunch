package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 他の最適化 MOD (JustEnoughThreads 等) の存在を検知し、
 * 重複フックや競合を未然に防ぐ動的 Mixin 構成プラグイン。
 */
public class FastLaunchMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/MixinPlugin");
    private boolean isJustEnoughThreadsPresent = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            // JustEnoughThreads / jeioptimize の存在をクラスローダーで検知
            Class.forName("com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext", false, getClass().getClassLoader());
            isJustEnoughThreadsPresent = true;
            LOGGER.warn("=======================================================================");
            LOGGER.warn("[FastLaunch] ⚠️ Detected 'JustEnoughThreads' (jeioptimize) mod!");
            LOGGER.warn("[FastLaunch] ⚠️ Automatically disabling FastLaunch JEI Plugin parallelization to avoid conflicts!");
            LOGGER.warn("=======================================================================");
        } catch (ClassNotFoundException ignored) {
            isJustEnoughThreadsPresent = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // JustEnoughThreads が存在する場合、JEI PluginCaller の並列化 Mixin を自動無効化
        if (isJustEnoughThreadsPresent) {
            if (mixinClassName.endsWith("JeiPluginCallerParallelMixin")) {
                LOGGER.info("[FastLaunch] ℹ️ Auto-disabled {} due to JustEnoughThreads presence.", mixinClassName);
                return false;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
