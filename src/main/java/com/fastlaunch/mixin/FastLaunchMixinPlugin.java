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
    private boolean isForgeryPresent = false;

    @Override
    public void onLoad(String mixinPackage) {
        // 0. Windows P-Core Affinity 最適化: Mixin 変換フェーズから P コアに固定
        com.fastlaunch.core.FastLaunchCpuAffinityEngine.applyPcoreAffinity();

        // 1. Core i5-13600KF等の高コアCPUにおけるCPU 100%飽和・ファンスパイクを根本抑制
        // バニラのリソースリロードスレッド（Worker-ResourceReload-0..18）を最大6スレッドにスロットリング
        int targetThreads = com.fastlaunch.config.FastLaunchConfig.PARALLEL_WORKER_THREADS;
        if (System.getProperty("max.bg.threads") == null) {
            System.setProperty("max.bg.threads", String.valueOf(targetThreads));
            LOGGER.info("[FastLaunch] 🎯 Throttled Minecraft background executor threads: max.bg.threads = {}", targetThreads);
        }

        // 2. Forge ModWorkManager のスレッド（modloading-worker-0..19）を最大6スレッドに抑制
        try {
            Class<?> fmlConfigClass = Class.forName("net.minecraftforge.fml.loading.FMLConfig", false, getClass().getClassLoader());
            Class<?> configValueClass = Class.forName("net.minecraftforge.fml.loading.FMLConfig$ConfigValue", false, getClass().getClassLoader());
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object maxThreadsEnum = Enum.valueOf((Class<Enum>) configValueClass, "MAX_THREADS");
            java.lang.reflect.Method updateConfig = fmlConfigClass.getMethod("updateConfig", configValueClass, Object.class);
            updateConfig.invoke(null, maxThreadsEnum, targetThreads);
            LOGGER.info("[FastLaunch] 🎯 Throttled Forge ModWorkManager threads: MAX_THREADS = {}", targetThreads);
        } catch (Throwable ignored) {}

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

        // 3. Forgery / Fabrication MOD の動的検知 (Issue #4 競合回避)
        try {
            Class.forName("com.unascribed.fabrication.support.injection.FailsoftModifyArgInjectionInfo", false, getClass().getClassLoader());
            isForgeryPresent = true;
        } catch (Throwable ignored) {
            try {
                Class.forName("com.unascribed.fabrication.FabricationMod", false, getClass().getClassLoader());
                isForgeryPresent = true;
            } catch (Throwable ignored2) {
                try {
                    java.nio.file.Path modsDir = net.minecraftforge.fml.loading.FMLPaths.MODSDIR.get();
                    if (modsDir != null && java.nio.file.Files.exists(modsDir)) {
                        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(modsDir)) {
                            isForgeryPresent = stream.anyMatch(p -> {
                                String name = p.getFileName().toString().toLowerCase();
                                return name.contains("forgery") || name.contains("fabrication");
                            });
                        }
                    }
                } catch (Throwable ignored3) {}
            }
        }

        if (isForgeryPresent) {
            LOGGER.warn("=======================================================================");
            LOGGER.warn("[FastLaunch] ⚠️ Detected 'Forgery' (Fabrication) mod!");
            LOGGER.warn("[FastLaunch] ⚠️ Automatically disabling early class preloading to prevent MixinEntity conflicts!");
            LOGGER.warn("=======================================================================");
            com.fastlaunch.core.ClassPreloadEngine.setForgeryPresent(true);
        } else {
            // 4. 最序盤からの投機的クラス＆ConfigSpecバックグラウンド並列ウォームアップ起動 (Forgery非導入時のみ安全に発動)
            try {
                com.fastlaunch.core.ClassPreloadEngine.startAsyncClassPreloading();
            } catch (Throwable ignored) {}
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
        // Forgery が存在する場合、干渉リスクのある Mixin を自動無効化
        if (isForgeryPresent) {
            if (mixinClassName.endsWith("FastLaunchSupermartijnModelGuardMixin") ||
                mixinClassName.endsWith("FastLaunchSophisticatedBackpacksGuardMixin")) {
                LOGGER.info("[FastLaunch] ℹ️ Auto-disabled {} due to Forgery compatibility mode.", mixinClassName);
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
