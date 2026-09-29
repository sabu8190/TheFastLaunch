package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchCreativeTabCacheEngine;
import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.CreativeModeTabRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * CreativeModeTabRegistry.recalculateItemCreativeModeTabs をインターセプトし、
 * 事前計算済みディスクキャッシュからトポロジカルソート結果を即座に復元する Mixin。
 */
@Mixin(value = CreativeModeTabRegistry.class, remap = false)
public abstract class FastLaunchCreativeTabMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/CreativeTabCache");

    @Shadow(remap = false)
    private static void setCreativeModeTabOrder(List<CreativeModeTab> sorted) {}

    @Inject(method = "recalculateItemCreativeModeTabs", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onRecalculateItemCreativeModeTabsHead(CallbackInfo ci) {
        long start = System.currentTimeMillis();
        List<CreativeModeTab> cached = FastLaunchCreativeTabCacheEngine.loadCachedTabs();
        if (cached != null && !cached.isEmpty()) {
            setCreativeModeTabOrder(cached);
            long elapsed = Math.max(0, System.currentTimeMillis() - start);
            LOGGER.info("[CreativeTabCache] ⚡ CreativeModeTabs restored from disk cache in {} ms (Skipped graph toposort)!", elapsed);
            FastLaunchSuccessLogger.recordActiveFeature(
                    "CreativeTab-Cache", 
                    String.format("ACTIVE [Restored %d tabs in %d ms]", cached.size(), elapsed)
            );
            ci.cancel();
        }
    }

    @Inject(method = "recalculateItemCreativeModeTabs", at = @At("RETURN"), remap = false)
    private static void onRecalculateItemCreativeModeTabsReturn(CallbackInfo ci) {
        List<CreativeModeTab> sorted = CreativeModeTabRegistry.getSortedCreativeModeTabs();
        if (sorted != null && !sorted.isEmpty()) {
            FastLaunchCreativeTabCacheEngine.saveCachedTabs(sorted);
        }
    }
}
