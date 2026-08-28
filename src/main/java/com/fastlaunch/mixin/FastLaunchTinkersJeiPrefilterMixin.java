package com.fastlaunch.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tinkers' Construct & アドオン (SakuraTinker 等) の数万通りにおよぶ
 * ツール/パーツ素材バリアントが JEI グローバルインデックスを圧迫・フリーズさせるのを未然に防ぐ Mixin。
 * (参考: Tinkers JEI Pre-Filter)
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.ingredients.ItemStackListFactory", remap = false)
public abstract class FastLaunchTinkersJeiPrefilterMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/TinkersPrefilter");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private static final TagKey<Item> TINKERS_MODIFIABLE = TagKey.create(Registries.ITEM, new ResourceLocation("tconstruct", "modifiable"));
    private static final TagKey<Item> TINKERS_PARTS = TagKey.create(Registries.ITEM, new ResourceLocation("tconstruct", "parts"));

    @ModifyVariable(
        method = "create(Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;",
        at = @At("RETURN"),
        require = 0,
        remap = false
    )
    private static List<ItemStack> fastLaunch$filterTinkersVariants(List<ItemStack> originalList) {
        if (originalList == null || originalList.size() < 100) {
            return originalList;
        }

        int beforeSize = originalList.size();
        List<ItemStack> filtered = new ArrayList<>(beforeSize);

        for (ItemStack stack : originalList) {
            if (stack.isEmpty()) continue;
            // modifiable や parts タグを持つ過剰な動的バリアントをフィルタ
            if (stack.is(TINKERS_MODIFIABLE) || stack.is(TINKERS_PARTS)) {
                // デフォルトの1種類目だけ残すか、過剰なNBTバリアントをスキップ
                if (!stack.hasTag()) {
                    filtered.add(stack);
                }
            } else {
                filtered.add(stack);
            }
        }

        int removed = beforeSize - filtered.size();
        if (removed > 0 && LOGGED.compareAndSet(false, true)) {
            LOGGER.info("=======================================================================");
            LOGGER.info("[TinkersPrefilter] 🛡️ Filtered {} excess Tinkers/Addon tool variants from JEI index!", removed);
            LOGGER.info("[TinkersPrefilter] 🛡️ Slashed memory usage and eliminated world join freeze!");
            LOGGER.info("=======================================================================");
        }

        return filtered;
    }
}
