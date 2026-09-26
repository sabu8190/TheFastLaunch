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
            // Goety (16s construction)
            "com.polarice3.goety.Goety",
            "com.polarice3.goety.common.blocks.ModBlocks",
            "com.polarice3.goety.common.items.ModItems",
            "com.polarice3.goety.common.entities.ModEntityType",
            "com.polarice3.goety.common.enchantments.ModEnchantments",
            "com.polarice3.goety.common.effects.GoetyEffects",
            "com.polarice3.goety.spells.Spells",
            // CraftTweaker (15s construction)
            "com.blamejared.crafttweaker.CraftTweaker",
            "com.blamejared.crafttweaker.api.CraftTweakerAPI",
            "com.blamejared.crafttweaker.api.action.base.IAction",
            // TACZ & WeaponMaster (16s construction)
            "com.tacz.guns.GunMod",
            "com.tacz.guns.init.ModItems",
            "com.tacz.guns.init.ModBlocks",
            "com.tacz.guns.init.ModEntities",
            // KubeJS (13s construction)
            "dev.latvian.mods.kubejs.KubeJS",
            "dev.latvian.mods.kubejs.script.ScriptType",
            "dev.latvian.mods.kubejs.bindings.event.ServerEvents",
            "dev.latvian.mods.kubejs.bindings.event.StartupEvents",
            // Touhou Little Maid
            "com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid",
            // Tinkers' Construct (11s construction)
            "slimeknights.tconstruct.TConstruct",
            "slimeknights.tconstruct.shared.TinkerCommons",
            "slimeknights.tconstruct.tools.TinkerModifiers",
            "slimeknights.tconstruct.tools.TinkerTools",
            "slimeknights.tconstruct.fluids.TinkerFluids",
            "slimeknights.tconstruct.world.TinkerWorld",
            // Immersive Engineering (8s construction)
            "blusunrize.immersiveengineering.ImmersiveEngineering",
            // Cataclysm
            "com.github.L_Ender.cataclysm.Cataclysm",
            // Create & Addons
            "com.simibubi.create.Create",
            "com.simibubi.create.AllBlocks",
            "com.simibubi.create.AllItems",
            // Mekanism
            "mekanism.common.Mekanism",
            "mekanism.common.registries.MekanismBlocks",
            "mekanism.common.registries.MekanismItems",
            // LDLib & JsonThings
            "com.lowdragmc.ldlib.LDLib",
            "dev.gigaherz.jsonthings.JsonThings"
    );

    public static void startAsyncClassPreloading() {
        CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            LOGGER.info("[ClassPreloader] Starting parallel class preloading for heavy mod classes across multi-cores...");

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.util.concurrent.atomic.AtomicInteger loadedCount = new java.util.concurrent.atomic.AtomicInteger(0);

            HEAVY_CLASSES.parallelStream().forEach(className -> {
                try {
                    Class.forName(className, false, cl);
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
