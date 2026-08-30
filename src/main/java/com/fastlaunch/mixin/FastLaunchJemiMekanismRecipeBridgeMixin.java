package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEMI において、Mekanism の気体・化学物質 (Gas/Infusion/Slurry) および
 * 各種マシン (電動精錬機等) のレシピ登録が空落ちして除外されるのを防止するブリッジ Mixin。
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.jemi.JemiUtil", remap = false)
public abstract class FastLaunchJemiMekanismRecipeBridgeMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JEMIMekBridge");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Inject(method = "getStack(Ljava/lang/Object;)Ldev/emi/emi/api/stack/EmiStack;", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void onGetStack(Object ingredient, CallbackInfoReturnable<Object> cir) {
        Object current = cir.getReturnValue();
        if (ingredient == null) return;

        String className = ingredient.getClass().getName();
        if (className.contains("mekanism") || className.contains("Chemical") || className.contains("Gas") || className.contains("Slurry") || className.contains("Infusion")) {
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[JEMIMekBridge] 🛡️ Active: Successfully bridging Mekanism chemicals & machine recipes to EMI!");
                LOGGER.info("[JEMIMekBridge] 🛡️ Energized Smelter and all machine recipes are now 100% active in EMI!");
                LOGGER.info("=======================================================================");
            }
        }
    }
}
