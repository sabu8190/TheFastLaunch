package com.fastlaunch.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

/**
 * MultiPackResourceManager.listResources のマルチコア並列化 Mixin。
 * 
 * 392個の JAR パックに跨がる多数のネームスペースの走査（Mantle や ModelBakery 等が毎回呼ぶ処理）を
 * FastLaunch 共有スレッドプールで並列化し、1回の探索に 5〜6秒 かかっていたボトルネックを大幅短縮します。
 */
@Mixin(value = MultiPackResourceManager.class, priority = 450)
public abstract class FastLaunchResourceManagerCacheMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ResourceManagerCache");

    @Shadow @Final
    private Map<String, FallbackResourceManager> namespacedManagers;

    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
    private void onListResourcesParallel(String directory, java.util.function.Predicate<ResourceLocation> filter, 
                                        CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir) {
        if (this.namespacedManagers == null || this.namespacedManagers.size() < 4) {
            return;
        }

        try {
            Map<ResourceLocation, Resource> result = new TreeMap<>();
            List<FallbackResourceManager> managers = new ArrayList<>(this.namespacedManagers.values());

            com.fastlaunch.core.FastLaunchThreadHelper.executeParallel(managers, manager -> {
                try {
                    Map<ResourceLocation, Resource> subMap = manager.listResources(directory, filter);
                    if (subMap != null && !subMap.isEmpty()) {
                        synchronized (result) {
                            result.putAll(subMap);
                        }
                    }
                } catch (Throwable ignored) {}
            });

            cir.setReturnValue(result);
        } catch (Throwable ignored) {
            // 例外時はバニラの直列フォールバック
        }
    }
}
