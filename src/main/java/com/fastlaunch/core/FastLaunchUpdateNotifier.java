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
 * TheFastLaunch 新バージョン自動更新通知エンジン (CurseForge & GitHub デュアルリンク対応)。
 */
public class FastLaunchUpdateNotifier {
    public static final String CURRENT_VERSION = "b1.5";
    public static final String UPDATE_CHECK_URL = "https://raw.githubusercontent.com/sabu8190/TheFastLaunch/main/update.json";
    public static final String CURSEFORGE_PAGE_URL = "https://www.curseforge.com/minecraft/mc-mods/thefastlaunch-tfl";
    public static final String GITHUB_RELEASE_URL = "https://github.com/sabu8190/TheFastLaunch/releases";
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/UpdateNotifier");

    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean NOTIFIED = new AtomicBoolean(false);
    private static volatile String latestVersion = null;
    private static volatile boolean updateAvailable = false;

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String getLatestVersion() {
        return latestVersion != null ? latestVersion : CURRENT_VERSION;
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
                            String foundVer = json.substring(start, end).replace("v-", "").replace("v", "").trim();
                            latestVersion = foundVer;
                            if (isStrictlyNewerVersion(foundVer, CURRENT_VERSION)) {
                                updateAvailable = true;
                                LOGGER.info("[UpdateNotifier] 🚀 New update found: TheFastLaunch v{} (Current: v{})", latestVersion, CURRENT_VERSION);
                            } else {
                                updateAvailable = false;
                                LOGGER.info("[UpdateNotifier] ✅ TheFastLaunch is up to date: v{}", CURRENT_VERSION);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("[UpdateNotifier] Silent update check notice: " + t.getMessage());
            }
        });
    }

    private static boolean isStrictlyNewerVersion(String latest, String current) {
        if (latest == null || current == null) return false;
        String cleanLatest = latest.toLowerCase().replace("b", "").replace("v", "").trim();
        String cleanCurrent = current.toLowerCase().replace("b", "").replace("v", "").trim();

        if (cleanLatest.equals(cleanCurrent)) {
            return false;
        }

        try {
            String[] lParts = cleanLatest.split("\\.");
            String[] cParts = cleanCurrent.split("\\.");
            int len = Math.max(lParts.length, cParts.length);
            for (int i = 0; i < len; i++) {
                int lVal = i < lParts.length ? Integer.parseInt(lParts[i]) : 0;
                int cVal = i < cParts.length ? Integer.parseInt(cParts[i]) : 0;
                if (lVal > cVal) return true;
                if (lVal < cVal) return false;
            }
        } catch (Exception e) {
            // パース失敗時は文字列比較
            return !cleanLatest.equals(cleanCurrent);
        }
        return false;
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
                
                // CurseForge リンク
                MutableComponent cfLink = Component.literal(" 👉 [CurseForgeで開く]").withStyle(
                    Style.EMPTY
                        .withColor(ChatFormatting.GOLD)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, CURSEFORGE_PAGE_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("ブラウザで CurseForge ページを開きます")))
                );

                // GitHub リンク
                MutableComponent ghLink = Component.literal("   👉 [GitHubで開く]").withStyle(
                    Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, GITHUB_RELEASE_URL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("ブラウザで GitHub Releases を開きます")))
                );

                msg.append(cfLink);
                msg.append(ghLink);
                msg.append(Component.literal("\n=====================================================").withStyle(ChatFormatting.DARK_AQUA));

                mc.player.sendSystemMessage(msg);
            }
        } catch (Throwable ignored) {}
    }
}
