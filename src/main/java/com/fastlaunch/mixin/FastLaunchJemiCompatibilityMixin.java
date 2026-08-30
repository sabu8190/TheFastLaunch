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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEMI (JEI + EMI 併用) 環境において、EMI がアクティブな場合に
 * JEI の GUI 描画（drawScreen / drawOnForeground / drawTooltips）のみ抑制し、
 * JEI の内部ロジック（isListDisplayed / runtime / レシピ登録）は完全に温存する。
 *
 * 重要: isListDisplayed() を false にすると JEI runtime が正常動作しなくなり、
 * JEMI のレシピ引継ぎや AE2 の JEI 同期検索が完全に壊れるため、絶対に抑制しない。
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
     * EMI がアクティブな場合、JEI の drawScreen 描画のみスキップ（内部ロジックは温存）。
     */
    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onDrawScreen(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (isEmiActive()) {
            if (LOGGED.compareAndSet(false, true)) {
                LOGGER.info("=======================================================================");
                LOGGER.info("[JEMICompat] 🛡️ EMI detected! JEI draw-only suppression active.");
                LOGGER.info("[JEMICompat] 🛡️ JEI runtime & recipe registration fully preserved!");
                LOGGER.info("[JEMICompat] 🛡️ JEMI recipe bridging & AE2 JEI-sync search: OPERATIONAL!");
                LOGGER.info("=======================================================================");
            }
            ci.cancel();
        }
    }

    /**
     * EMI がアクティブな場合、JEI の drawOnForeground 描画のみスキップ。
     */
    @Inject(method = "drawOnForeground", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onDrawOnForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (isEmiActive()) {
            ci.cancel();
        }
    }

    /**
     * EMI がアクティブな場合、JEI の ツールチップ描画のみスキップ。
     */
    @Inject(method = "drawTooltips", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onDrawTooltips(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (isEmiActive()) {
            ci.cancel();
        }
    }
}

