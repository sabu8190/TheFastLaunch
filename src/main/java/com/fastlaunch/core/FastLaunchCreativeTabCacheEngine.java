package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * クリエイティブタブ トポロジカルソート結果のディスクキャッシュエンジン。
 * 初回計算結果（タブ表示順序）を fastlaunch_cache/creative_tabs.cache に保存し、
 * 2回目以降の重い Guava MutableGraph 構築およびトポロジカルソート処理を完全バイパスする。
 */
public class FastLaunchCreativeTabCacheEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/CreativeTabCache");
    private static final String CACHE_FILE = "fastlaunch_cache/creative_tabs.cache";
    private static final String MAGIC = "TFL_TAB_CACHE_V1";
    private static long cachedModpackHash = 0L;

    public static List<CreativeModeTab> loadCachedTabs() {
        try {
            File gameDir = FMLPaths.GAMEDIR.get().toFile();
            File cacheFile = new File(gameDir, CACHE_FILE);
            if (!cacheFile.exists() || cacheFile.length() == 0) {
                return null;
            }

            long currentHash = getModpackHash(gameDir);
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile), 8192))) {
                String magic = in.readUTF();
                if (!MAGIC.equals(magic)) {
                    return null;
                }
                long storedHash = in.readLong();
                if (storedHash != currentHash) {
                    LOGGER.info("[CreativeTabCache] Modpack changes detected, invalidating creative tab cache.");
                    return null;
                }

                int count = in.readInt();
                List<CreativeModeTab> result = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String locStr = in.readUTF();
                    ResourceLocation loc = new ResourceLocation(locStr);
                    CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(loc);
                    if (tab == null) {
                        LOGGER.warn("[CreativeTabCache] Cached tab not found in registry: {}. Invalidating cache.", locStr);
                        return null;
                    }
                    result.add(tab);
                }
                return result;
            }
        } catch (Throwable t) {
            LOGGER.debug("[CreativeTabCache] Error loading cached tabs: {}", t.getMessage());
            return null;
        }
    }

    public static void saveCachedTabs(List<CreativeModeTab> tabs) {
        if (tabs == null || tabs.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                File gameDir = FMLPaths.GAMEDIR.get().toFile();
                File cacheDir = new File(gameDir, "fastlaunch_cache");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                File cacheFile = new File(gameDir, CACHE_FILE);
                File tempFile = new File(gameDir, CACHE_FILE + ".tmp");

                long currentHash = getModpackHash(gameDir);
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile), 8192))) {
                    out.writeUTF(MAGIC);
                    out.writeLong(currentHash);
                    out.writeInt(tabs.size());

                    for (CreativeModeTab tab : tabs) {
                        ResourceLocation name = CreativeModeTabRegistry.getName(tab);
                        if (name != null) {
                            out.writeUTF(name.toString());
                        } else {
                            // 名前が引けない特殊タブの場合はフォールバック
                            ResourceLocation regName = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                            out.writeUTF(regName != null ? regName.toString() : "minecraft:dummy");
                        }
                    }
                }

                if (cacheFile.exists()) {
                    cacheFile.delete();
                }
                tempFile.renameTo(cacheFile);

                LOGGER.info("[CreativeTabCache] 💾 Successfully cached {} creative tab ordering to disk.", tabs.size());
            } catch (Throwable t) {
                LOGGER.warn("[CreativeTabCache] Failed to save creative tab cache: {}", t.getMessage());
            }
        }, FastLaunchThreadHelper.getSharedWorkerPool());
    }

    private static synchronized long getModpackHash(File gameDir) {
        if (cachedModpackHash != 0L) {
            return cachedModpackHash;
        }
        File modsDir = new File(gameDir, "mods");
        if (!modsDir.exists()) return 0L;
        try {
            cachedModpackHash = Files.walk(modsDir.toPath(), 1)
                    .filter(p -> p.toString().endsWith(".jar") && !p.getFileName().toString().contains("TheFastLaunch") && !p.getFileName().toString().contains("fastlaunch"))
                    .mapToLong(p -> {
                        File f = p.toFile();
                        return f.length() ^ (f.lastModified() * 31);
                    })
                    .sum();
        } catch (Throwable e) {
            cachedModpackHash = 0L;
        }
        return cachedModpackHash;
    }
}
