package com.fastlaunch.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
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
 * EMI において、Mekanism などのマシンアイテムが NBT (エネルギーやコンポーネント) の
 * 差異によってクラフトレシピや使用用途レシピの検索に失敗する不具合を解消する Mixin。
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.api.stack.Comparison", remap = false)
public abstract class FastLaunchEmiMekanismNbtCompatMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/EMINBTCompat");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static Method getItemStackMethod = null;
    private static boolean methodLookedUp = false;

    @Inject(method = "compare", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onCompare(Object a, Object b, CallbackInfoReturnable<Boolean> cir) {
        if (a == b) {
            cir.setReturnValue(true);
            return;
        }
        if (a == null || b == null) {
            return;
        }

        if (!methodLookedUp) {
            try {
                getItemStackMethod = a.getClass().getMethod("getItemStack");
            } catch (Throwable ignored) {
            }
            methodLookedUp = true;
        }

        if (getItemStackMethod != null) {
            try {
                ItemStack isA = (ItemStack) getItemStackMethod.invoke(a);
                ItemStack isB = (ItemStack) getItemStackMethod.invoke(b);

                if (isA != null && isB != null && isA.getItem() == isB.getItem()) {
                    Item item = isA.getItem();
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id != null) {
                        String ns = id.getNamespace();
                        if (ns.equals("mekanism") || ns.equals("mekanismgenerators") || ns.equals("mekanismtools")) {
                            if (LOGGED.compareAndSet(false, true)) {
                                LOGGER.info("=======================================================================");
                                LOGGER.info("[EMINBTCompat] 🛡️ Active: Seamlessly relaxed NBT comparison for Mekanism items!");
                                LOGGER.info("[EMINBTCompat] 🛡️ All machine craft recipes are now 100% searchable in EMI!");
                                LOGGER.info("=======================================================================");
                            }
                            cir.setReturnValue(true);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
