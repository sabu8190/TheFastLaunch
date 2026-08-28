package com.fastlaunch.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.Util;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;

/**
 * タイトル画面上に CurseForge と GitHub の両方を選んで開けるスタイリッシュな
 * TheFastLaunch アップデート通知バナーを描画＆クリック対応。
 */
public class FastLaunchTitleScreenNotifier {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/TitleNotifier");
    public static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods/thefastlaunch-tfl";
    public static final String GITHUB_URL = "https://github.com/sabu8190/TheFastLaunch/releases";

    // バナー全体の描画位置とサイズ
    private static final int BANNER_X = 10;
    private static final int BANNER_Y = 10;
    private static final int BANNER_W = 340;
    private static final int BANNER_H = 24;

    // ボタンの相対位置
    // タイトル: 🚀 TheFastLaunch (vX.X)
    // [CurseForge] ボタン: X = BANNER_X + 185, W = 70, H = 16
    // [GitHub] ボタン: X = BANNER_X + 260, W = 70, H = 16

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        if (!FastLaunchUpdateNotifier.isUpdateAvailable()) {
            return;
        }

        // タイトル画面または進行画面でのみ描画
        if (event.getScreen() instanceof TitleScreen || event.getScreen().getClass().getName().contains("TitleScreen") || event.getScreen().getClass().getName().contains("FancyMenu")) {
            GuiGraphics graphics = event.getGuiGraphics();
            Minecraft mc = Minecraft.getInstance();
            int mouseX = event.getMouseX();
            int mouseY = event.getMouseY();

            int btnCfX = BANNER_X + 185;
            int btnCfY = BANNER_Y + 4;
            int btnCfW = 70;
            int btnCfH = 16;

            int btnGhX = BANNER_X + 260;
            int btnGhY = BANNER_Y + 4;
            int btnGhW = 70;
            int btnGhH = 16;

            boolean cfHovered = mouseX >= btnCfX && mouseX <= btnCfX + btnCfW && mouseY >= btnCfY && mouseY <= btnCfY + btnCfH;
            boolean ghHovered = mouseX >= btnGhX && mouseX <= btnGhX + btnGhW && mouseY >= btnGhY && mouseY <= btnGhY + btnGhH;

            // 背景ボックス (ダーク半透明 + シアン/ゴールド枠線)
            graphics.fill(BANNER_X, BANNER_Y, BANNER_X + BANNER_W, BANNER_Y + BANNER_H, 0xEE0A1420);
            graphics.renderOutline(BANNER_X, BANNER_Y, BANNER_W, BANNER_H, 0xFFFFAA00);

            // バナーテキスト
            String latestVer = FastLaunchUpdateNotifier.getLatestVersion();
            String label = "🚀 TFL (v" + latestVer + ") 更新可能:";
            graphics.drawString(mc.font, label, BANNER_X + 8, BANNER_Y + 8, 0xFF55FFFF, false);

            // [CurseForge] ボタン
            int cfBg = cfHovered ? 0xFFE04E22 : 0xAA802810; // CurseForge オレンジ
            int cfBorder = cfHovered ? 0xFFFFAA00 : 0xFF888888;
            int cfText = cfHovered ? 0xFFFFFFFF : 0xFFFFAA88;
            graphics.fill(btnCfX, btnCfY, btnCfX + btnCfW, btnCfY + btnCfH, cfBg);
            graphics.renderOutline(btnCfX, btnCfY, btnCfW, btnCfH, cfBorder);
            graphics.drawCenteredString(mc.font, "CurseForge", btnCfX + (btnCfW / 2), btnCfY + 4, cfText);

            // [GitHub] ボタン
            int ghBg = ghHovered ? 0xFF238636 : 0xAA10441C; // GitHub グリーン
            int ghBorder = ghHovered ? 0xFF55FF55 : 0xFF888888;
            int ghText = ghHovered ? 0xFFFFFFFF : 0xFF88FFAA;
            graphics.fill(btnGhX, btnGhY, btnGhX + btnGhW, btnGhY + btnGhH, ghBg);
            graphics.renderOutline(btnGhX, btnGhY, btnGhW, btnGhH, ghBorder);
            graphics.drawCenteredString(mc.font, "GitHub", btnGhX + (btnGhW / 2), btnGhY + 4, ghText);
        }
    }

    @SubscribeEvent
    public void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!FastLaunchUpdateNotifier.isUpdateAvailable()) {
            return;
        }

        if (event.getScreen() instanceof TitleScreen || event.getScreen().getClass().getName().contains("TitleScreen") || event.getScreen().getClass().getName().contains("FancyMenu")) {
            double mouseX = event.getMouseX();
            double mouseY = event.getMouseY();

            int btnCfX = BANNER_X + 185;
            int btnCfY = BANNER_Y + 4;
            int btnCfW = 70;
            int btnCfH = 16;

            int btnGhX = BANNER_X + 260;
            int btnGhY = BANNER_Y + 4;
            int btnGhW = 70;
            int btnGhH = 16;

            // CurseForge クリック判定
            if (mouseX >= btnCfX && mouseX <= btnCfX + btnCfW && mouseY >= btnCfY && mouseY <= btnCfY + btnCfH) {
                try {
                    Util.getPlatform().openUri(new URI(CURSEFORGE_URL));
                    event.setCanceled(true);
                    LOGGER.info("[TitleNotifier] Opened CurseForge release page.");
                } catch (Throwable t) {
                    LOGGER.error("Failed to open CurseForge URL: " + t.getMessage());
                }
                return;
            }

            // GitHub クリック判定
            if (mouseX >= btnGhX && mouseX <= btnGhX + btnGhW && mouseY >= btnGhY && mouseY <= btnGhY + btnGhH) {
                try {
                    Util.getPlatform().openUri(new URI(GITHUB_URL));
                    event.setCanceled(true);
                    LOGGER.info("[TitleNotifier] Opened GitHub release page.");
                } catch (Throwable t) {
                    LOGGER.error("Failed to open GitHub URL: " + t.getMessage());
                }
            }
        }
    }
}
