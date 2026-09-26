package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Create / Catnip の StitchedSprite において、マルチスレッド並列 MOD 初期化時（CONSTRUCT）に
 * 非スレッドセーフな HashMap.computeIfAbsent が複数スレッドから同時呼出されて
 * ConcurrentModificationException（TFMG等の初期化失敗）が発生するのを 100% 防止する安全ガード Mixin。
 */
@Pseudo
@Mixin(targets = "net.createmod.catnip.render.StitchedSprite", remap = false)
public abstract class FastLaunchCatnipStitchedSpriteGuardMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/StitchedSpriteGuard");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Shadow(remap = false)
    private static Map ALL;

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 0, remap = false)
    private static void onClinit(CallbackInfo ci) {
        try {
            ALL = Collections.synchronizedMap(new HashMap<>());
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[FastLaunch] 🛡️ Guarded Catnip/Create StitchedSprite.ALL map with synchronized wrapper!");
                LOGGER.info("=======================================================================");
                com.fastlaunch.logging.FastLaunchSuccessLogger.recordActiveFeature(
                        "Catnip-StitchedSpriteGuard", 
                        "ACTIVE [Protected StitchedSprite from CME during parallel construct]"
                );
            }
        } catch (Throwable t) {
            LOGGER.warn("[FastLaunch] Warning wrapping StitchedSprite.ALL: {}", t.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
            method = "<init>(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 0,
            remap = false
    )
    private Object onSafeComputeIfAbsent(Map map, Object key, Function mappingFunction) {
        synchronized (map) {
            return map.computeIfAbsent(key, mappingFunction);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
            method = "<init>(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"),
            require = 0,
            remap = false
    )
    private boolean onSafeListAdd(List list, Object element) {
        synchronized (list) {
            return list.add(element);
        }
    }
}
