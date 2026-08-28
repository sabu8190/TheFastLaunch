package com.fastlaunch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEMI (JEI + EMI 併用) 環境において、EMI がアクティブな場合に
 * JEI の GUI オーバーレイ (アイテムリスト・検索バー・ページネーション) が
 * EMI と重複描画されるのを自動防止し、EMI のみに綺麗に切り替える互換 Mixin。
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public abstract class FastLaunchJemiCompatibilityMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/JEMICompat");
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    private static Boolean emiLoadedCache = null;

    private static boolean isEmiActive() {
        if (emiLoadedCache == null) {
            try {
                emiLoadedCache = ModList.get().isLoaded("emi");
            } catch (Throwable t) {
                emiLoadedCache = false;
            }
        }
        return emiLoadedCache;
    }

    /**
     * EMI がロードされている場合、JEI の isListDisplayed() を false にして
     * JEI 側のリスト表示を完全に抑制する。
     */
    @Inject(method = "isListDisplayed", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onIsListDisplayed(CallbackInfoReturnable<Boolean> cir) {
        if (isEmiActive()) {
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[JEMICompat] 🛡️ EMI detected! Suppressed JEI IngredientListOverlay duplicate rendering.");
                LOGGER.info("[JEMICompat] 🛡️ Seamlessly active: Clean EMI UI only (No UI overlap)!");
                LOGGER.info("=======================================================================");
            }
            cir.setReturnValue(false);
        }
    }

    /**
     * EMI がアクティブな場合、JEI の drawScreen 描画を直接スキップ。
     */
    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onDrawScreen(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (isEmiActive()) {
            ci.cancel();
        }
    }

    /**
     * EMI がアクティブな場合、JEI の drawOnForeground 描画を直接スキップ。
     */
    @Inject(method = "drawOnForeground", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onDrawOnForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (isEmiActive()) {
            ci.cancel();
        }
    }

    /**
     * EMI がアクティブな場合、JEI の ツールチップ描画重複を直接スキップ。
     */
    @Inject(method = "drawTooltips", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onDrawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (isEmiActive()) {
            ci.cancel();
        }
    }
}
