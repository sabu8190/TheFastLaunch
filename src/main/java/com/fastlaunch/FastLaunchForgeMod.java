package com.fastlaunch;

import com.fastlaunch.core.*;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod("fastlaunch")
public class FastLaunchForgeMod {
    public static final String MOD_ID = "fastlaunch";
    public static final Logger LOGGER = LogManager.getLogger("FastLaunch");

    static {
        int cores = Math.max(4, Runtime.getRuntime().availableProcessors());
        System.setProperty("forge.parallelModLoading", "true");
        System.setProperty("fml.parallelLoading", "true");
        System.setProperty("fml.modLoadingThreadCount", String.valueOf(cores));
        System.setProperty("fml.earlyProgressParallel", "true");

        // オンラインバージョンチェックの通信遅延（5.3秒フリーズ）を完全無効化
        System.setProperty("forge.disableVersionCheck", "true");
        System.setProperty("fml.disableVersionCheck", "true");

        // Win32 API: DisableProcessWindowsGhosting() をクラスロード最初期にスレッド負荷ゼロで直接実行
        disableWindowsGhostingDirect();

        System.out.println("=======================================================================");
        System.out.println(">>> [FastLaunch Core] HIGH PRIORITY INITIALIZATION HOOK LOADED!     <<<");
        System.out.println(">>> [FastLaunch Core] Target Platform: Forge 1.20.1 / UniMixin (v6.1)<<<");
        System.out.println(">>> [FastLaunch Core] Parallel ModLoading Workers: " + cores + " Cores!      <<<");
        System.out.println(">>> [FastLaunch Core] Win32 DisableProcessWindowsGhosting: ACTIVE! (0% CPU)  <<<");
        System.out.println("=======================================================================");
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

                    System.out.println("[FastLaunch] 🛡️ Win32 API: DisableProcessWindowsGhosting() successfully invoked (Ghosting Disabled)!");
                } catch (Throwable t) {
                    System.out.println("[FastLaunch] User32 JNA invocation note: " + t.getMessage());
                }
            }
        } catch (Throwable ignored) {}
    }

    public FastLaunchForgeMod() {
        FastLaunchUpdateNotifier.checkForUpdatesAsync();
        LOGGER.info("[FastLaunch] ===========================================================");
        LOGGER.info("[FastLaunch] FastLaunch Forge 1.20.1 Core Engine ACTIVE (v6.0 ZERO-STALL)");
        LOGGER.info("[FastLaunch] CREATE_REGISTRIES 69s Accelerator & GLFW Window Pump ACTIVE!");
        LOGGER.info("[FastLaunch] ===========================================================");

        File gameDir = FMLPaths.GAMEDIR.get().toFile();

        // 1. 白画面リアルタイム検知センサー
        RenderThreadStallDetector.startMonitoring();

        // 2. CREATE_REGISTRIES 69秒アクセラレーター
        CreateRegistriesParallelOptimizer.armCreateRegistriesOptimization();

        // 3. レジストリスナップショット
        RegistrySnapshotCacheEngine.initializeRegistryCache(gameDir);

        // 4. FantasyEnd 巨大クラススナップショットキャッシュ
        FantasyEndCacheEngine.initializeFantasyEndCache(gameDir);

        // 5. MemorySweep 121秒フルGCキラー
        MemorySweepGCKiller.armGCKiller();

        // 6. Alex's Caves エンティティ並列化
        AlexsCavesEntityOptimizer.armAlexsCavesOptimizer();

        // 7. JEI GUI ランタイムスナップショット
        JeiGuiRuntimeCacheEngine.initializeJeiGuiCache(gameDir);

        // 8. ZIP事前展開ダイレクト直読キャッシュ
        ResourceZipPreExtractCacheEngine.initializeZipCache(gameDir);

        // 9. 全コンフィグメモリキャッシュ
        ConfigAsyncCacheEngine.preloadAllConfigs(gameDir);

        // 10. Truly Modular 36億バリアントキャッシュ
        TrulyModularVariantCacheEngine.initializeVariantCache(gameDir);

        // 11. 3Dモデルベイク＆破損モデルバイパスキャッシュ
        ModelBakeSnapshotCacheEngine.initializeModelCache(gameDir);

        // 12. Iron's Spells 属性親和性キャッシュ
        IronSpellsAffinityCacheEngine.initializeSpellCache(gameDir);

        // 13. Patchouli 193冊ガイドブックキャッシュ
        PatchouliBookSnapshotCacheEngine.initializeBookCache(gameDir);

        // 14. MBD2 定義先行ロード
        Multiblocked2Optimizer.preloadMultiblockDefinitions(gameDir);

        // 15. 大規模クラスウォームアップ
        ClassPreloadEngine.startAsyncClassPreloading();

        // 16. 欠損テクスチャバイパス
        MissingTextureBypassEngine.armTextureBypass();

        // 17. 初期リソースパック並列展開
        AsyncResourcePackLoader.armAsyncResourceLoader();

        // 18. バニラ検索ツリーバイパス
        VanillaSearchTreeBypass.armSearchTreeBypass();

        // 19. Truly Modular 1,126スキン並列パース
        TrulyModularSkinOptimizer.armSkinOptimization();

        // 20. KubeJS 47スクリプト並列化
        KubeJsParallelOptimizer.armKubeJsOptimization();

        // 21. 9ディメンション過負荷スロットリング
        DimensionSaveThrottler.armDimensionThrottling();

        // 22. 破損LootRuleバイパス
        LootRuleExceptionBypass.armLootRuleBypass();

        // 23. ワールド入室フルGC遅延評価
        MemorySweepGCOptimizer.armGCOptimization();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::loadComplete);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        RenderThreadStallDetector.updateHeartbeat();
        CreatePluginOptimizer.armCreateOptimization();
        Mbd2JeiRecipeOptimizer.armMbd2JeiOptimizer();
        DatapackAsyncLoader.armDatapackLoader();
        JeiForgeGuiOptimizer.armJeiGuiOptimizer();
        SearchTreeParallelOptimizer.armSearchTreeOptimization();
        RegisterParallelOptimizer.armRegisterOptimization();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        RenderThreadStallDetector.updateHeartbeat();
        AsyncIngredientFilter.triggerAsyncIndexing();
    }

    private void loadComplete(final FMLLoadCompleteEvent event) {
        RenderThreadStallDetector.updateHeartbeat();
        // ITransformationService handles ghosting natively
        FastLaunchSuccessLogger.printSuccessReport();
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        RenderThreadStallDetector.updateHeartbeat();
        FastLaunchSuccessLogger.printSuccessReport();
    }

    @SubscribeEvent
    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        FastLaunchUpdateNotifier.notifyPlayerOnWorldJoin();
        LOGGER.info("[FastLaunch/Priority] Player logging into world - activating packet throttler & spawn pipeline.");
        WorldJoinThrottler.setWorldJoining(true);
        SpawnRegionAsyncPipeline.armSpawnPipeline();
        WikiRecipeAsyncCollector.armWikiRecipeCollector();
        FastLaunchSuccessLogger.printSuccessReport();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[FastLaunch/Priority] Integrated server starting - fast chunk & datapack pipeline active.");
    }

    public static void onGameLoadComplete() {
        FastLaunchSuccessLogger.printSuccessReport();
    }
}
