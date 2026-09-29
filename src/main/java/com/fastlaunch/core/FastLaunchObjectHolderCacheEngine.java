package com.fastlaunch.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Loading Registries / postRegisterEvents (22.3秒) の中で最大のボトルネックである
 * 何万個もの ObjectHolder ハンドラへの全件フラット走査（50レジストリ × 2万ハンドラ = 100万回走査）を、
 * レジストリ別 O(1) ハッシュインデックス化により O(InjectedFields) へ超高速化するエンジン。
 */
public class FastLaunchObjectHolderCacheEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/ObjectHolderCache");

    // レジストリ名別にグループ化されたハンドラマップ
    private static final Map<ResourceLocation, List<Consumer<Predicate<ResourceLocation>>>> INDEXED_HOLDERS = new ConcurrentHashMap<>();
    // リフレクションでレジストリが特定できなかったフォールバック用ハンドラ
    private static final List<Consumer<Predicate<ResourceLocation>>> FALLBACK_HOLDERS = new CopyOnWriteArrayList<>();

    private static final AtomicBoolean INDEX_BUILT = new AtomicBoolean(false);
    private static int lastIndexedSize = -1;

    // パフォーマンス実測メトリクス
    private static final AtomicLong TOTAL_OPTIMIZED_TIME_MS = new AtomicLong(0);
    private static final AtomicInteger TOTAL_INVOCATIONS = new AtomicInteger(0);
    private static final AtomicInteger TOTAL_EXECUTED_HOLDERS = new AtomicInteger(0);

    private static Field registryField = null;
    private static boolean reflectionFailed = false;

    public static void initializeObjectHolderCache(java.io.File gameDir) {
        try {
            java.io.File cacheDir = new java.io.File(gameDir, "fastlaunch_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            LOGGER.info("[ObjectHolderCache] 🎯 FastLaunch ObjectHolder directory ready.");
        } catch (Throwable t) {
            LOGGER.debug("[ObjectHolderCache] Notice: {}", t.getMessage());
        }
    }

    /**
     * rawSet から各 ObjectHolderRef の対象レジストリを特定し、インデックスを構築
     */
    public static synchronized void ensureIndexed(Set<Consumer<Predicate<ResourceLocation>>> rawSet) {
        if (rawSet == null) return;
        if (INDEX_BUILT.get() && rawSet.size() == lastIndexedSize) {
            return;
        }

        long start = System.currentTimeMillis();
        INDEXED_HOLDERS.clear();
        FALLBACK_HOLDERS.clear();

        for (Consumer<Predicate<ResourceLocation>> holder : rawSet) {
            ResourceLocation regName = extractRegistryName(holder);
            if (regName != null) {
                INDEXED_HOLDERS.computeIfAbsent(regName, k -> new ArrayList<>()).add(holder);
            } else {
                FALLBACK_HOLDERS.add(holder);
            }
        }

        lastIndexedSize = rawSet.size();
        INDEX_BUILT.set(true);
        long elapsed = Math.max(0, System.currentTimeMillis() - start);

        LOGGER.info("[ObjectHolderIndex] 🎯 Indexed {} object holders into {} registries in {} ms (Fallback: {}).",
                rawSet.size(), INDEXED_HOLDERS.size(), elapsed, FALLBACK_HOLDERS.size());
    }

    private static ResourceLocation extractRegistryName(Consumer<Predicate<ResourceLocation>> holder) {
        if (reflectionFailed || holder == null) return null;
        try {
            if (registryField == null) {
                Class<?> clazz = holder.getClass();
                // net.minecraftforge.registries.ObjectHolderRef
                while (clazz != null && !clazz.equals(Object.class)) {
                    try {
                        Field f = clazz.getDeclaredField("registry");
                        f.setAccessible(true);
                        registryField = f;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        clazz = clazz.getSuperclass();
                    }
                }
                if (registryField == null) {
                    reflectionFailed = true;
                    return null;
                }
            }

            Object regObj = registryField.get(holder);
            if (regObj instanceof ForgeRegistry<?> forgeReg) {
                return forgeReg.getRegistryName();
            }
        } catch (Throwable t) {
            LOGGER.debug("[ObjectHolderIndex] Notice extracting registry: {}", t.getMessage());
        }
        return null;
    }

    /**
     * O(1) レジストリキーマッチングによる超高速バッチ適用
     */
    public static void applyOptimized(Set<Consumer<Predicate<ResourceLocation>>> rawSet, Predicate<ResourceLocation> filter) {
        if (rawSet == null || rawSet.isEmpty()) return;

        ensureIndexed(rawSet);

        long start = System.currentTimeMillis();
        RuntimeException aggregate = new RuntimeException("Failed to apply some object holders, see suppressed exceptions for details");
        int executedThisPass = 0;

        // 1. 各レジストリキーについて filter.test(regName) を評価（キー数は約50個のみ）
        for (Map.Entry<ResourceLocation, List<Consumer<Predicate<ResourceLocation>>>> entry : INDEXED_HOLDERS.entrySet()) {
            ResourceLocation regName = entry.getKey();
            if (filter.test(regName)) {
                List<Consumer<Predicate<ResourceLocation>>> holders = entry.getValue();
                for (Consumer<Predicate<ResourceLocation>> holder : holders) {
                    try {
                        holder.accept(filter);
                        executedThisPass++;
                    } catch (Exception e) {
                        aggregate.addSuppressed(e);
                    }
                }
            }
        }

        // 2. フォールバックハンドラがあれば実行
        for (Consumer<Predicate<ResourceLocation>> holder : FALLBACK_HOLDERS) {
            try {
                holder.accept(filter);
                executedThisPass++;
            } catch (Exception e) {
                aggregate.addSuppressed(e);
            }
        }

        long elapsed = Math.max(0, System.currentTimeMillis() - start);
        TOTAL_OPTIMIZED_TIME_MS.addAndGet(elapsed);
        TOTAL_EXECUTED_HOLDERS.addAndGet(executedThisPass);
        int invCount = TOTAL_INVOCATIONS.incrementAndGet();

        // 定期ロギング（初回および一定回数ごと、または遅延検知時）
        if (invCount == 1 || invCount % 20 == 0) {
            LOGGER.info("[ObjectHolderFastApply] ⚡ Pass #{}: Processed {} matching holders in {} ms (Total time: {} ms).",
                    invCount, executedThisPass, elapsed, TOTAL_OPTIMIZED_TIME_MS.get());
        }

        if (aggregate.getSuppressed().length > 0) {
            throw aggregate;
        }
    }

    public static long getTotalOptimizedTimeMs() {
        return TOTAL_OPTIMIZED_TIME_MS.get();
    }

    public static int getTotalInvocations() {
        return TOTAL_INVOCATIONS.get();
    }
}
