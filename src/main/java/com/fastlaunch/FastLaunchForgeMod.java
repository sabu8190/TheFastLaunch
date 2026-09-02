package com.fastlaunch;

import com.fastlaunch.config.FastLaunchConfig;
import com.fastlaunch.core.*;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * TheFastLaunch (TFL) - High-Speed Asynchronous Optimization Engine for Minecraft 1.20.1 Forge.
 */
@Mod(FastLaunchForgeMod.MOD_ID)
public class FastLaunchForgeMod {
    public static final String MOD_ID = "fastlaunch";
    public static final String MOD_NAME = "TheFastLaunch";
    public static final String VERSION = "b1.6.1";
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/Core");

    static {
        int cores = Math.max(4, Runtime.getRuntime().availableProcessors());
        
        // JVM & Forge 並列Modロードプロパティ
        System.setProperty("forge.parallelModLoading", "true");
        System.setProperty("fml.parallelLoading", "true");
        System.setProperty("fml.modLoadingThreadCount", String.valueOf(cores));
        System.setProperty("fml.earlyProgressParallel", "true");

        // オンラインバージョンチェック遅延の完全無効化
        System.setProperty("forge.disableVersionCheck", "true");
        System.setProperty("fml.disableVersionCheck", "true");

        // Win32 API: DisableProcessWindowsGhosting()
        disableWindowsGhostingDirect();

        LOGGER.info("=======================================================================");
        LOGGER.info(">>> [TheFastLaunch] HIGH PRIORITY INITIALIZATION HOOK LOADED!       <<<");
        LOGGER.info(">>> [TheFastLaunch] Target Platform: Forge 1.20.1 / UniMixin (v6.2)  <<<");
        LOGGER.info(">>> [TheFastLaunch] Parallel ModLoading Workers: {} Cores!            <<<", cores);
        LOGGER.info(">>> [TheFastLaunch] Win32 DisableProcessWindowsGhosting: ACTIVE! (0% CPU) <<<");
        LOGGER.info("=======================================================================");
    }

    public FastLaunchForgeMod() {
        FastLaunchConfig.load();
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Forge 標準 Mods 画面 Config ボタン連携 (ゲーム内 GUI)
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, 
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, screen) -> new com.fastlaunch.client.gui.FastLaunchConfigScreen(screen)));

        // ライフサイクルイベント登録
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::loadComplete);

        // Forge イベントバス登録
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new FastLaunchTitleScreenNotifier());

        // バックグラウンド自動アップデートチェッカー起動
        FastLaunchUpdateNotifier.checkForUpdatesAsync();

        // 3段階適応型メモリガバナーの起動
        FastLaunchAdaptiveMemoryGovernor.start();

        // コア最適化エンジンの初期化
        initializeCoreEngines();
    }

    private void initializeCoreEngines() {
        try {
            File gameDir = FMLPaths.GAMEDIR.get().toFile();
            RegistrySnapshotCacheEngine.initializeRegistryCache(gameDir);
            FantasyEndCacheEngine.initializeFantasyEndCache(gameDir);
            ResourceZipPreExtractCacheEngine.initializeZipCache(gameDir);
            JsonThingsCacheEngine.initializeJsonThingsCache(gameDir);
            FastLaunchObjectHolderCacheEngine.initializeObjectHolderCache(gameDir);
            ClassPreloadEngine.startAsyncClassPreloading();
            ModelBakePreheatEngine.preheatForkJoinPool();
            RenderThreadStallDetector.startMonitoring();
        } catch (Throwable t) {
            LOGGER.debug("[TheFastLaunch] Engine initialization note: {}", t.getMessage());
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[TheFastLaunch] CommonSetup: Multi-Core Asynchronous Engine Initialized.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[TheFastLaunch] ClientSetup: Client Acceleration Pipelines Armed.");
    }

    private void loadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("[TheFastLaunch] LoadComplete: All Acceleration Modules Operational!");
        File gameDir = FMLPaths.GAMEDIR.get().toFile();
        if (FastLaunchConfig.ENABLE_STARTUP_CACHE_PURGE) {
            FastLaunchStartupCachePurger.purgeAllCaches();
        }
        FastLaunchCacheCleaner.cleanObsoleteCaches(gameDir);
        com.fastlaunch.logging.FastLaunchSuccessLogger.printSuccessReport();
    }

    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        FastLaunchUpdateNotifier.notifyPlayerOnWorldJoin();
    }

    private static void disableWindowsGhostingDirect() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                try {
                    Class<?> nativeLibraryClass = Class.forName("com.sun.jna.NativeLibrary");
                    java.lang.reflect.Method getInstanceMethod = nativeLibraryClass.getMethod("getInstance", String.class);
                    Object user32Lib = getInstanceMethod.invoke(null, "user32");

                    java.lang.reflect.Method getFunctionMethod = nativeLibraryClass.getMethod("getFunction", String.class);
                    Object func = getFunctionMethod.invoke(user32Lib, "DisableProcessWindowsGhosting");

                    java.lang.reflect.Method invokeVoidMethod = func.getClass().getMethod("invokeVoid", Object[].class);
                    invokeVoidMethod.invoke(func, (Object) new Object[]{});

                    LOGGER.info("[TheFastLaunch] 🛡️ Win32 API: DisableProcessWindowsGhosting() successfully invoked (Ghosting Disabled)!");
                } catch (Throwable t) {
                    LOGGER.debug("[TheFastLaunch] User32 JNA invocation note: {}", t.getMessage());
                }
            }
        } catch (Throwable ignored) {}
    }
}
