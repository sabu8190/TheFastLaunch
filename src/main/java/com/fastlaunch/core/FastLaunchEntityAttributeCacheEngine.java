package com.fastlaunch.core;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * EntityAttributeModificationEvent の結果をディスクに永続化するキャッシュエンジン。
 * Cataclysm, Goety, Born in Chaos などの 20+ 個の MOD が全エンティティに対して回す
 * 直列2重ループ（実測 14.35 秒）を完全バイパスし、事前計算された属性定義を数ミリ秒で復元する。
 */
public class FastLaunchEntityAttributeCacheEngine {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/EntityAttributeCache");
    private static final String CACHE_FILE = "fastlaunch_cache/entity_attributes.cache";
    private static final String MAGIC = "TFL_ATTR_CACHE_V1";
    private static long cachedModpackHash = 0L;
    private static Field builderMapField = null;

    static {
        try {
            // AttributeSupplier.Builder の内部マップフィールドを取得 (builder または f_22262_)
            try {
                builderMapField = AttributeSupplier.Builder.class.getDeclaredField("builder");
            } catch (NoSuchFieldException e) {
                builderMapField = AttributeSupplier.Builder.class.getDeclaredField("f_22262_");
            }
            builderMapField.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.debug("[EntityAttributeCache] Builder map field resolution notice: {}", t.getMessage());
        }
    }

    /**
     * ディスクキャッシュから属性定義を復元して targetMap に投入する。
     * @return キャッシュの復元に成功した場合は true、ミスまたは無効な場合は false
     */
    public static boolean applyCachedAttributes(Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> targetMap) {
        try {
            File gameDir = FMLPaths.GAMEDIR.get().toFile();
            File cacheFile = new File(gameDir, CACHE_FILE);
            if (!cacheFile.exists() || cacheFile.length() == 0) {
                return false;
            }

            long currentHash = getModpackHash(gameDir);
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile), 65536))) {
                String magic = in.readUTF();
                if (!MAGIC.equals(magic)) {
                    return false;
                }
                long storedHash = in.readLong();
                if (storedHash != currentHash) {
                    LOGGER.info("[EntityAttributeCache] Modpack changes detected, invalidating entity attribute cache.");
                    return false;
                }

                int entityCount = in.readInt();
                int totalAttributesAdded = 0;

                for (int i = 0; i < entityCount; i++) {
                    String entityTypeIdStr = in.readUTF();
                    ResourceLocation entityLoc = new ResourceLocation(entityTypeIdStr);
                    EntityType<?> rawType = ForgeRegistries.ENTITY_TYPES.getValue(entityLoc);

                    int attrCount = in.readInt();
                    if (rawType instanceof EntityType) {
                        @SuppressWarnings("unchecked")
                        EntityType<? extends LivingEntity> entityType = (EntityType<? extends LivingEntity>) rawType;
                        AttributeSupplier.Builder builder = targetMap.computeIfAbsent(entityType, k -> new AttributeSupplier.Builder());

                        for (int j = 0; j < attrCount; j++) {
                            String attrIdStr = in.readUTF();
                            boolean hasCustomValue = in.readBoolean();
                            double val = in.readDouble();

                            ResourceLocation attrLoc = new ResourceLocation(attrIdStr);
                            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attrLoc);
                            if (attr != null) {
                                if (hasCustomValue) {
                                    builder.add(attr, val);
                                } else {
                                    builder.add(attr);
                                }
                                totalAttributesAdded++;
                            }
                        }
                    } else {
                        // 未知のエンティティ型（Mod削除など）の場合はストリームをスキップ
                        for (int j = 0; j < attrCount; j++) {
                            in.readUTF();
                            in.readBoolean();
                            in.readDouble();
                        }
                    }
                }

                LOGGER.info("[EntityAttributeCache] ⚡ Restored attributes for {} entity types ({} total attributes injected) from disk cache!",
                        entityCount, totalAttributesAdded);
                return true;
            }
        } catch (Throwable t) {
            LOGGER.warn("[EntityAttributeCache] Failed to load entity attribute cache: {}", t.getMessage());
            return false;
        }
    }

    /**
     * 通常イベントディスパッチ完了後の map を非同期でディスクに保存する。
     */
    public static void saveAttributes(Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> sourceMap) {
        if (sourceMap == null || sourceMap.isEmpty() || builderMapField == null) return;

        // 保存用スナップショットをメインスレッド上で軽量作成
        Map<String, Map<String, Double>> snapshot = new HashMap<>();
        try {
            for (Map.Entry<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> entry : sourceMap.entrySet()) {
                ResourceLocation entityLoc = ForgeRegistries.ENTITY_TYPES.getKey(entry.getKey());
                if (entityLoc == null) continue;

                @SuppressWarnings("unchecked")
                Map<Attribute, AttributeInstance> attrMap = (Map<Attribute, AttributeInstance>) builderMapField.get(entry.getValue());
                if (attrMap == null || attrMap.isEmpty()) continue;

                Map<String, Double> attrSnap = new HashMap<>();
                for (Map.Entry<Attribute, AttributeInstance> attrEntry : attrMap.entrySet()) {
                    ResourceLocation attrLoc = ForgeRegistries.ATTRIBUTES.getKey(attrEntry.getKey());
                    if (attrLoc == null) continue;
                    double baseValue = attrEntry.getValue() != null ? attrEntry.getValue().getBaseValue() : Double.NaN;
                    attrSnap.put(attrLoc.toString(), baseValue);
                }
                if (!attrSnap.isEmpty()) {
                    snapshot.put(entityLoc.toString(), attrSnap);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[EntityAttributeCache] Failed to snapshot attributes for caching: {}", t.getMessage());
            return;
        }

        // バックグラウンドスレッドでディスクへフラッシュ
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
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile), 65536))) {
                    out.writeUTF(MAGIC);
                    out.writeLong(currentHash);
                    out.writeInt(snapshot.size());

                    for (Map.Entry<String, Map<String, Double>> e : snapshot.entrySet()) {
                        out.writeUTF(e.getKey()); // entity ID
                        Map<String, Double> attrs = e.getValue();
                        out.writeInt(attrs.size());
                        for (Map.Entry<String, Double> attrEntry : attrs.entrySet()) {
                            out.writeUTF(attrEntry.getKey()); // attr ID
                            double val = attrEntry.getValue();
                            boolean hasVal = !Double.isNaN(val);
                            out.writeBoolean(hasVal);
                            out.writeDouble(hasVal ? val : 0.0);
                        }
                    }
                }

                if (cacheFile.exists()) {
                    cacheFile.delete();
                }
                tempFile.renameTo(cacheFile);

                LOGGER.info("[EntityAttributeCache] 💾 Persisted attribute cache for {} entity types to disk.", snapshot.size());
            } catch (Throwable t) {
                LOGGER.warn("[EntityAttributeCache] Error writing entity attribute cache: {}", t.getMessage());
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
