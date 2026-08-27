package com.fastlaunch.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TheFastLaunch 新バージョン自動更新通知エンジン。
 */
public class FastLaunchUpdateNotifier {
    public static final String CURRENT_VERSION = "b1.2";
    private static final String UPDATE_CHECK_URL = "https://raw.githubusercontent.com/sabu8190/TheFastLaunch/main/update.json";
    private static final String RELEASE_PAGE_URL = "https://github.com/sabu8190/TheFastLaunch/releases";
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/UpdateNotifier");

    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean NOTIFIED = new AtomicBoolean(false);
    private static volatile String latestVersion = null;
    private static volatile boolean updateAvailable = false;

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String getLatestVersion() {
        return latestVersion != null ? latestVersion : "b1.3";
    }

    public static void checkForUpdatesAsync() {
        if (!CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(UPDATE_CHECK_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", "TheFastLaunch-UpdateChecker");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    String json = sb.toString();
                    String key = "\"1.20.1-latest\":";
                    if (json.contains(key)) {
                        int idx = json.indexOf(key);
                        int start = json.indexOf("\"", idx + key.length()) + 1;
                        int end = json.indexOf("\"", start);
                        if (start > 0 && end > start) {
                            latestVersion = json.substring(start, end).replace("v-", "").replace("v", "").trim();
                            if (!CURRENT_VERSION.equalsIgnoreCase(latestVersion) && isNewerVersion(latestVersion, CURRENT_VERSION)) {
                                updateAvailable = true;
                                LOGGER.info("[UpdateNotifier] 🚀 New update found: TheFastLaunch v{} (Current: v{})", latestVersion, CURRENT_VERSION);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("[UpdateNotifier] Silent update check notice: " + t.getMessage());
            }
        });
    }

    private static boolean isNewerVersion(String latest, String current) {
        try {
            String lNum = latest.replaceAll("[^0-9.]", "");
            String cNum = current.replaceAll("[^0-9.]", "");
            return !lNum.equals(cNum);
        } catch (Exception e) {
            return false;
        }
    }

    public static void notifyPlayerOnWorldJoin() {
        if (!updateAvailable || !NOTIFIED.compareAndSet(false, true)) {
            return;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MutableComponent msg = Component.literal("=====================================================\n").withStyle(ChatFormatting.DARK_AQUA);
                msg.append(Component.literal(" [TheFastLaunch] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                msg.append(Component.literal("🚀 新しいバージョン (v" + latestVersion + ") が利用可能です！\n").withStyle(ChatFormatting.AQUA));
                
                MutableComponent clickMsg = Component.literal(" 👉 [ここをクリックして最新版をダウンロード]").withStyle(
                    Style.EMPTY
                        .withColor(ChatFormatting.YELLOW)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RELEASE_PAGE_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("ブラウザで GitHub Releases を開きます")))
                );
                msg.append(clickMsg);
                msg.append(Component.literal("\n=====================================================").withStyle(ChatFormatting.DARK_AQUA));

                mc.player.sendSystemMessage(msg);
            }
        } catch (Throwable ignored) {}
    }
}
