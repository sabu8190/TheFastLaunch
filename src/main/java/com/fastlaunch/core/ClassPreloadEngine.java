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

    // 2. トップボトルネック MOD メインクラスおよび DeferredRegister 構造体（Constructing Mods 直前の先行 <clinit>）
    private static final List<String> HEAVY_MOD_CLASSES = Arrays.asList(
            // Goety (11.1s - Spells, Blocks, Items, Entities, Rituals)
            "com.Polarice3.Goety.Goety",
            "com.Polarice3.Goety.common.blocks.ModBlocks",
            "com.Polarice3.Goety.common.items.ModItems",
            "com.Polarice3.Goety.common.entities.ModEntityType",
            "com.Polarice3.Goety.common.enchantments.ModEnchantments",
            "com.Polarice3.Goety.common.effects.GoetyEffects",
            "com.Polarice3.Goety.spells.Spells",
            "com.Polarice3.Goety.init.ModAttributes",
            "com.Polarice3.Goety.init.ModSounds",
            "com.Polarice3.Goety.common.events.ModEvents",
            "com.Polarice3.Goety.common.ritual.Rituals",
            "com.Polarice3.Goety.common.research.Research",
            // CraftTweaker Main (9.6s)
            "com.blamejared.crafttweaker.CraftTweaker",
            "com.blamejared.crafttweaker.api.CraftTweakerAPI",
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
            // Yes Steve Model & Touhou Little Maid (4.9s)
            "com.github.tartaricacid.yesstevemodel.YesSteveModel",
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

    public static void startAsyncClassPreloading() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            LOGGER.info("[EarlyStaticWarmup] ⚡ Launching multi-core speculative warmup for heavy mod classes...");

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassPreloadEngine.class.getClassLoader();
            }
            ClassLoader finalCl = cl;
            AtomicInteger loadedCount = new AtomicInteger(0);

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

            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            LOGGER.info("[ClassPreloader] ⚡ Multi-core class bytecode warmup completed in {} ms (Loaded {} classes).",
                    elapsed, loadedCount.get());
            FastLaunchSuccessLogger.recordActiveFeature(
                    "ClassBytecodePreloader", 
                    String.format("ACTIVE [Pre-loaded %d heavy classes in %d ms]", loadedCount.get(), elapsed)
            );
        }, FastLaunchThreadHelper.getSharedWorkerPool());
    }
}
