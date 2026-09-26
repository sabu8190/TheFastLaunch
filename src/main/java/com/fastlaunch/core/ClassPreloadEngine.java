package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 大規模 Mod クラス＆リソース並列プリロードエンジン v2.0。
 * FantasyEnd (com.mega.uom), Essential, ldlib のアイテム・魔法書・ツールをフル並列ウォームアップ。
 */
public class ClassPreloadEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ClassPreloader");
    private static final java.util.concurrent.atomic.AtomicBoolean STARTED = new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final List<String> HEAVY_CLASSES = Arrays.asList(
            // FantasyEnd & Mega UOM
            "com.mega.uom.ModSource",
            "com.mega.uom.world.biome.FantasyEndBiomes",
            "com.mega.uom.block.FantasyEndBlocks",
            "com.mega.uom.item.FantasyEndItems",
            // Youkais Homecoming (Caused 5s stall on YHBlocks.<clinit>)
            "dev.xkmc.youkaishomecoming.init.registrate.YHBlocks",
            "dev.xkmc.youkaishomecoming.init.registrate.YHItems",
            "dev.xkmc.youkaishomecoming.init.YoukaisHomecoming",
            // Goety (14.2s - Fixed casing from com.polarice3 to com.Polarice3.Goety)
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
            // WeaponMaster (11.5s - com.sky.weaponmaster)
            "com.sky.weaponmaster.SweeperWeapon",
            "com.sky.weaponmaster.AicMoodCapability",
            "com.sky.weaponmaster.ModCreativeTab",
            "com.sky.weaponmaster.PieceAsset",
            "com.sky.weaponmaster.SweeperWeaponTooltipComponent",
            "com.sky.weaponmaster.RuniaConf",
            // KubeJS & Rhino JS Engine (11.8s)
            "dev.latvian.mods.kubejs.KubeJS",
            "dev.latvian.mods.kubejs.script.ScriptType",
            "dev.latvian.mods.kubejs.bindings.event.ServerEvents",
            "dev.latvian.mods.kubejs.bindings.event.StartupEvents",
            "dev.latvian.mods.rhino.Context",
            "dev.latvian.mods.rhino.Scriptable",
            "dev.latvian.mods.rhino.ScriptableObject",
            "dev.latvian.mods.rhino.Function",
            "dev.latvian.mods.rhino.JavaAdapter",
            "dev.latvian.mods.rhino.NativeJavaClass",
            "dev.latvian.mods.rhino.NativeJavaObject",
            "dev.latvian.mods.rhino.ClassShutter",
            // CraftTweaker & ZenCode (10.0s)
            "com.blamejared.crafttweaker.CraftTweaker",
            "com.blamejared.crafttweaker.api.CraftTweakerAPI",
            "com.blamejared.crafttweaker.api.action.base.IAction",
            "com.blamejared.crafttweaker.api.zencode.IScriptLoader",
            "com.blamejared.crafttweaker.api.ingredient.IIngredient",
            "com.blamejared.crafttweaker.api.item.IItemStack",
            // TACZ Guns
            "com.tacz.guns.GunMod",
            "com.tacz.guns.init.ModItems",
            "com.tacz.guns.init.ModBlocks",
            "com.tacz.guns.init.ModEntities",
            // Touhou Little Maid
            "com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid",
            // Tinkers' Construct (7.6s)
            "slimeknights.tconstruct.TConstruct",
            "slimeknights.tconstruct.shared.TinkerCommons",
            "slimeknights.tconstruct.tools.TinkerModifiers",
            "slimeknights.tconstruct.tools.TinkerTools",
            "slimeknights.tconstruct.fluids.TinkerFluids",
            "slimeknights.tconstruct.world.TinkerWorld",
            // Cataclysm (8.3s)
            "com.github.L_Ender.cataclysm.Cataclysm",
            "com.github.L_Ender.cataclysm.init.ModEntities",
            "com.github.L_Ender.cataclysm.init.ModItems",
            "com.github.L_Ender.cataclysm.init.ModBlocks",
            // Immersive Engineering
            "blusunrize.immersiveengineering.ImmersiveEngineering",
            // Create & Addons (6.9s)
            "com.simibubi.create.Create",
            "com.simibubi.create.AllBlocks",
            "com.simibubi.create.AllItems",
            // Mekanism (5.0s)
            "mekanism.common.Mekanism",
            "mekanism.common.registries.MekanismBlocks",
            "mekanism.common.registries.MekanismItems",
            // LDLib & JsonThings
            "com.lowdragmc.ldlib.LDLib",
            "dev.gigaherz.jsonthings.JsonThings"
    );

    public static void startAsyncClassPreloading() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            LOGGER.info("[ClassPreloader] Starting parallel class preloading for heavy mod classes across multi-cores...");

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = ClassPreloadEngine.class.getClassLoader();
            }
            ClassLoader finalCl = cl;
            java.util.concurrent.atomic.AtomicInteger loadedCount = new java.util.concurrent.atomic.AtomicInteger(0);

            FastLaunchThreadHelper.executeParallel(HEAVY_CLASSES, className -> {
                try {
                    Class.forName(className, false, finalCl);
                    loadedCount.incrementAndGet();
                } catch (Throwable ignored) {}
            });

            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            LOGGER.info("[ClassPreloader] Parallel class cache warmup completed in {} ms (Loaded {} classes).", elapsed, loadedCount.get());
            FastLaunchSuccessLogger.recordActiveFeature(
                    "ClassPreloader", 
                    String.format("ACTIVE [Pre-loaded %d mod classes in %d ms]", loadedCount.get(), elapsed)
            );
        }, FastLaunchThreadHelper.getSharedWorkerPool());
    }
}
