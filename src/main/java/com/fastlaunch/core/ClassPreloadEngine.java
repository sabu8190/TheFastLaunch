package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 大規模 Mod クラス＆リソース並列投機的ウォームアップエンジン v3.0 (Early Static Warmup)。
 * 静音スレッドガバナー（最大6スレッド、NORM_PRIORITY - 2）下で、
 * Goety (11.1s), CraftTweaker (9.6s), KubeJS (8.0s), WeaponMaster (6.5s) 等の
 * 最も重厚なクラス群のバイトコードロードおよび static 初期化子 (<clinit>) を先行実行し、
 * 本番 Constructing Mods ステージでの直列待機・クラスローダーロック競合を完全解消する。
 */
public class ClassPreloadEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ClassPreloader");
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    // 1. 純粋スクリプトエンジン・ASTパーサー・コンパイラ・基盤クラス（即時安全に <clinit> 可能）
    private static final List<String> ENGINE_CLASSES = Arrays.asList(
            // KubeJS & Rhino JS Engine (8.0s)
            "dev.latvian.mods.rhino.Context",
            "dev.latvian.mods.rhino.Scriptable",
            "dev.latvian.mods.rhino.ScriptableObject",
            "dev.latvian.mods.rhino.Function",
            "dev.latvian.mods.rhino.JavaAdapter",
            "dev.latvian.mods.rhino.NativeJavaClass",
            "dev.latvian.mods.rhino.NativeJavaObject",
            "dev.latvian.mods.rhino.ClassShutter",
            "dev.latvian.mods.rhino.mod.util.RhinoProperties",
            "dev.latvian.mods.kubejs.script.ScriptType",
            "dev.latvian.mods.kubejs.bindings.event.ServerEvents",
            "dev.latvian.mods.kubejs.bindings.event.StartupEvents",
            // CraftTweaker ZenCode Engine (9.6s)
            "com.blamejared.crafttweaker.api.zencode.IScriptLoader",
            "com.blamejared.crafttweaker.api.action.base.IAction",
            "com.blamejared.crafttweaker.api.ingredient.IIngredient",
            "com.blamejared.crafttweaker.api.item.IItemStack",
            "com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager",
            // WeaponMaster Engine & Capability (6.5s)
            "com.sky.weaponmaster.SweeperWeapon",
            "com.sky.weaponmaster.AicMoodCapability",
            "com.sky.weaponmaster.PieceAsset",
            "com.sky.weaponmaster.SweeperWeaponTooltipComponent",
            "com.sky.weaponmaster.RuniaConf"
    );

    // 2. トップボトルネック MOD メインクラスおよび DeferredRegister 構造体（Constructing Mods 直前の先行ロード）
    private static final List<String> HEAVY_MOD_CLASSES = Arrays.asList(
            // Goety (11.1s - Spells, Blocks, Items, Entities, Rituals)
            "com.Polarice3.Goety.Goety",
            "com.Polarice3.Goety.common.blocks.ModBlocks",
            "com.Polarice3.Goety.common.items.ModItems",
            "com.Polarice3.Goety.common.entities.ModEntityType",
            "com.Polarice3.Goety.common.entities.ally.ModAllyEntities",
            "com.Polarice3.Goety.common.enchantments.ModEnchantments",
            "com.Polarice3.Goety.common.effects.GoetyEffects",
            "com.Polarice3.Goety.spells.Spells",
            "com.Polarice3.Goety.init.ModAttributes",
            "com.Polarice3.Goety.init.ModSounds",
            "com.Polarice3.Goety.common.events.ModEvents",
            "com.Polarice3.Goety.common.ritual.Rituals",
            "com.Polarice3.Goety.common.research.Research",
            // CraftTweaker Main & Plugin Engine (9.6s)
            "com.blamejared.crafttweaker.CraftTweakerForge",
            "com.blamejared.crafttweaker.CraftTweakerCommon",
            "com.blamejared.crafttweaker.CraftTweaker",
            "com.blamejared.crafttweaker.api.CraftTweakerAPI",
            "com.blamejared.crafttweaker.impl.plugin.core.PluginManager",
            // KubeJS Main (8.0s)
            "dev.latvian.mods.kubejs.KubeJS",
            // Cataclysm (7.1s - Entities, Items, Blocks, Attributes)
            "com.github.L_Ender.cataclysm.Cataclysm",
            "com.github.L_Ender.cataclysm.init.ModEntities",
            "com.github.L_Ender.cataclysm.init.ModItems",
            "com.github.L_Ender.cataclysm.init.ModBlocks",
            "com.github.L_Ender.cataclysm.init.ModAttributes",
            // WeaponMaster ModCreativeTab (6.5s)
            "com.sky.weaponmaster.ModCreativeTab",
            // Mekanism Lasers & Core (6.7s)
            "pepjebs.mekanism_lasers.MekanismLasers",
            "pepjebs.mekanism_lasers.common.registries.MekanismLasersBlocks",
            "pepjebs.mekanism_lasers.common.registries.MekanismLasersItems",
            "mekanism.common.Mekanism",
            "mekanism.common.registries.MekanismBlocks",
            "mekanism.common.registries.MekanismItems",
            // Create (6.5s - Blocks, Items, Entities)
            "com.simibubi.create.Create",
            "com.simibubi.create.AllBlocks",
            "com.simibubi.create.AllItems",
            "com.simibubi.create.AllEntityTypes",
            // Tinkers' Construct (5.7s - Tools, Modifiers, Fluids)
            "slimeknights.tconstruct.TConstruct",
            "slimeknights.tconstruct.shared.TinkerCommons",
            "slimeknights.tconstruct.tools.TinkerModifiers",
            "slimeknights.tconstruct.tools.TinkerTools",
            "slimeknights.tconstruct.fluids.TinkerFluids",
            "slimeknights.tconstruct.world.TinkerWorld",
            // Yes Steve Model (YSM - 7.6s) & Touhou Little Maid (4.9s)
            "com.elfmcys.yesstevemodel.YesSteveModel",
            "com.elfmcys.yesstevemodel.oOoOO0o0ooO0oO000o0oOOoO",
            "com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid",
            // Youkais Homecoming (4.7s)
            "dev.xkmc.youkaishomecoming.init.YoukaisHomecoming",
            "dev.xkmc.youkaishomecoming.init.registrate.YHBlocks",
            "dev.xkmc.youkaishomecoming.init.registrate.YHItems",
            // TACZ Guns & Ammo
            "com.tacz.guns.GunMod",
            "com.tacz.guns.init.ModItems",
            "com.tacz.guns.init.ModBlocks",
            "com.tacz.guns.init.ModEntities",
            // FantasyEnd & LDLib
            "com.mega.uom.ModSource",
            "com.mega.uom.world.biome.FantasyEndBiomes",
            "com.mega.uom.block.FantasyEndBlocks",
            "com.mega.uom.item.FantasyEndItems",
            "com.lowdragmc.ldlib.LDLib",
            "dev.gigaherz.jsonthings.JsonThings"
    );

    // 3. EntityAttributeModificationEvent で参照される主要 LivingEntity クラス群（14.4秒停止の先行解消）
    private static final List<String> LIVING_ENTITY_CLASSES = Arrays.asList(
            "com.github.L_Ender.cataclysm.init.ModEntities",
            "com.github.L_Ender.cataclysm.init.ModAttribute",
            "com.Polarice3.Goety.common.entities.ModEntityType",
            "com.Polarice3.Goety.init.ModAttributes",
            "dev.xkmc.l2hostility.init.L2Hostility",
            "com.mega.uom.common.attribute.ModAttributes",
            "com.mega.endinglib.common.init.ModAttributes",
            "net.minecraft.world.entity.monster.Monster",
            "net.minecraft.world.entity.monster.Zombie",
            "net.minecraft.world.entity.monster.Skeleton",
            "net.minecraft.world.entity.monster.Creeper",
            "net.minecraft.world.entity.monster.EnderMan",
            "net.minecraft.world.entity.player.Player"
    );

    // 4. Goety 等の巨大 ForgeConfigSpec 先行静的ビルドウォームアップ（12.3秒のコンストラクタ停止を解消）
    private static final List<String> CONFIG_SPEC_WARMUP_CLASSES = Arrays.asList(
            "com.Polarice3.Goety.config.MainConfig",
            "com.Polarice3.Goety.config.SpellConfig",
            "com.Polarice3.Goety.config.MobsConfig",
            "com.Polarice3.Goety.config.ItemConfig",
            "com.Polarice3.Goety.config.BrewConfig",
            "com.Polarice3.Goety.config.AttributesConfig"
    );

    public static class GoetyConfigTarget {
        public final String className;
        public final String tomlRelPath;
        public GoetyConfigTarget(String className, String tomlRelPath) {
            this.className = className;
            this.tomlRelPath = tomlRelPath;
        }
    }

    private static final List<GoetyConfigTarget> GOETY_CONFIG_TARGETS = Arrays.asList(
            new GoetyConfigTarget("com.Polarice3.Goety.config.MainConfig", "goety/goety.toml"),
            new GoetyConfigTarget("com.Polarice3.Goety.config.AttributesConfig", "goety/goety-attributes.toml"),
            new GoetyConfigTarget("com.Polarice3.Goety.config.SpellConfig", "goety/goety-spells.toml"),
            new GoetyConfigTarget("com.Polarice3.Goety.config.BrewConfig", "goety/goety-brews.toml"),
            new GoetyConfigTarget("com.Polarice3.Goety.config.MobsConfig", "goety/goety-mobs.toml"),
            new GoetyConfigTarget("com.Polarice3.Goety.config.ItemConfig", "goety/goety-items.toml")
    );

    public static void startAsyncClassPreloading() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            LOGGER.info("[EarlyStaticWarmup] ⚡ Launching multi-core speculative warmup for heavy mod classes & ConfigSpecs...");

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassPreloadEngine.class.getClassLoader();
            }
            ClassLoader finalCl = cl;
            AtomicInteger loadedCount = new AtomicInteger(0);
            AtomicInteger configCount = new AtomicInteger(0);

            // Phase 0: YesSteveModel ネイティブコアライブラリ（DLL）の先行展開＆ロード（7.6秒のコンストラクタ停止を解消）
            CompletableFuture.runAsync(() -> {
                try {
                    Class<?> ysmHelper = Class.forName("com.elfmcys.yesstevemodel.oOoOO0o0ooO0oO000o0oOOoO", false, finalCl);
                    java.lang.reflect.Method loadMethod = ysmHelper.getDeclaredMethod("Oo0Oo0o00O00Oo0OOoOOoooo");
                    loadMethod.setAccessible(true);
                    loadMethod.invoke(null);
                    LOGGER.info("[EarlyStaticWarmup] ⚡ YesSteveModel native core library pre-extracted & loaded!");
                } catch (Throwable t) {
                    LOGGER.debug("[EarlyStaticWarmup] YSM native preload skipped: {}", t.getMessage());
                }
            }, FastLaunchThreadHelper.getSharedWorkerPool());

            // Phase 1: スクリプトエンジン・コンパイラ・基盤パーサーのクラスプリロード
            FastLaunchThreadHelper.executeParallel(ENGINE_CLASSES, className -> {
                try {
                    Class.forName(className, false, finalCl);
                    loadedCount.incrementAndGet();
                } catch (Throwable ignored) {}
            });

            // Phase 2: トップボトルネック MOD クラス群のクラスプリロード
            FastLaunchThreadHelper.executeParallel(HEAVY_MOD_CLASSES, className -> {
                try {
                    Class.forName(className, false, finalCl);
                    loadedCount.incrementAndGet();
                } catch (Throwable ignored) {}
            });

            // Phase 3: 主要 LivingEntity & 属性付与対象クラス群の先行ロード
            FastLaunchThreadHelper.executeParallel(LIVING_ENTITY_CLASSES, className -> {
                try {
                    Class.forName(className, false, finalCl);
                    loadedCount.incrementAndGet();
                } catch (Throwable ignored) {}
            });

            // Phase 4: Goety 巨大 ForgeConfigSpec の並列静的初期化 (<clinit> 先行実行)
            FastLaunchThreadHelper.executeParallel(CONFIG_SPEC_WARMUP_CLASSES, className -> {
                try {
                    // initialize = true で静的イニシャライザをバックグラウンド並列実行
                    Class.forName(className, true, finalCl);
                    configCount.incrementAndGet();
                } catch (Throwable ignored) {}
            });

            // Phase 5: Goety 6大 TOML 設定ファイルのマルチコア並列先行パース＆ロード（10.7秒のコンストラクタ停止を解消）
            FastLaunchThreadHelper.executeParallel(GOETY_CONFIG_TARGETS, target -> {
                try {
                    Class<?> cfgCls = Class.forName(target.className, true, finalCl);
                    java.lang.reflect.Field specField = cfgCls.getField("SPEC");
                    net.minecraftforge.common.ForgeConfigSpec spec = (net.minecraftforge.common.ForgeConfigSpec) specField.get(null);
                    if (spec != null && !spec.isLoaded()) {
                        java.lang.reflect.Method loadMethod = cfgCls.getMethod("loadConfig", net.minecraftforge.common.ForgeConfigSpec.class, String.class);
                        java.nio.file.Path path = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(target.tomlRelPath);
                        loadMethod.invoke(null, spec, path.toString());
                        configCount.incrementAndGet();
                    }
                } catch (Throwable ignored) {}
            });

            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            LOGGER.info("[ClassPreloader] ⚡ Multi-core class bytecode warmup completed in {} ms (Loaded {} classes, {} ConfigSpecs).",
                    elapsed, loadedCount.get(), configCount.get());
            FastLaunchSuccessLogger.recordActiveFeature(
                    "ClassBytecodePreloader", 
                    String.format("ACTIVE [Pre-loaded %d classes, %d ConfigSpecs in %d ms]", loadedCount.get(), configCount.get(), elapsed)
            );
        }, FastLaunchThreadHelper.getSharedWorkerPool());
    }
}
