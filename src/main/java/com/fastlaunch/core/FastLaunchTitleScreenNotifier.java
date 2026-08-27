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
 * タイトル画面上にスタイリッシュな TheFastLaunch アップデート通知バナーを描画＆クリック対応。
 */
public class FastLaunchTitleScreenNotifier {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/TitleNotifier");
    private static final String RELEASE_URL = "https://github.com/sabu8190/TheFastLaunch/releases";

    // バナーの描画位置とサイズ
    private static int bannerX = 10;
    private static int bannerY = 10;
    private static int bannerW = 260;
    private static int bannerH = 22;

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

            boolean hovered = mouseX >= bannerX && mouseX <= bannerX + bannerW && mouseY >= bannerY && mouseY <= bannerY + bannerH;

            // 背景ボックス (ダーク半透明 + シアン/ゴールド枠線)
            int bgColor = hovered ? 0xEE102030 : 0xCC05101A;
            int borderColor = hovered ? 0xFF55FFFF : 0xFFFFAA00;

            graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH, bgColor);
            graphics.renderOutline(bannerX, bannerY, bannerW, bannerH, borderColor);

            // テキスト描画
            String latestVer = FastLaunchUpdateNotifier.getLatestVersion();
            String text = "🚀 TheFastLaunch (v" + latestVer + ") 更新可能! [クリック]";
            int textColor = hovered ? 0xFFFFFF55 : 0xFF55FFFF;

            graphics.drawString(mc.font, text, bannerX + 8, bannerY + 7, textColor, false);
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

            if (mouseX >= bannerX && mouseX <= bannerX + bannerW && mouseY >= bannerY && mouseY <= bannerY + bannerH) {
                try {
                    Util.getPlatform().openUri(new URI(RELEASE_URL));
                    event.setCanceled(true);
                } catch (Throwable t) {
                    LOGGER.error("Failed to open release URL: " + t.getMessage());
                }
            }
        }
    }
}
