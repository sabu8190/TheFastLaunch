package com.fastlaunch.mixin;

import com.fastlaunch.logging.FastLaunchSuccessLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;

/**
 * MBD2 (Multiblocked 2) の NBT 定義ファイル（マシン・マルチブロック・レシピ等）を
 * マルチコア並列で高速パースし、決定論的かつ安全に登録する Mixin。
 */
@Pseudo
@Mixin(targets = "com.lowdragmc.mbd2.utils.FileUtils", remap = false)
public class Mbd2FileUtilsMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/MBD2ParallelLoader");

    private static ForkJoinPool getPool() {
        return com.fastlaunch.core.FastLaunchThreadHelper.getSharedWorkerPool();
    }

    @Inject(method = "loadNBTFiles", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void onLoadNBTFilesParallel(File dir, String suffix, BiConsumer<File, CompoundTag> consumer, CallbackInfo ci) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        // 1. サブディレクトリも含めて対象ファイルを完全再帰収集 (MBD2 の Files.walkFileTree 互換)
        List<File> targetFiles = new ArrayList<>();
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (path.getFileName().toString().endsWith(suffix)) {
                        targetFiles.add(path.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("[MBD2ParallelLoader] Failed to scan directory recursively, falling back to native MBD2 loader: {}", dir, t);
            return; // ci.cancel() を呼ばずに MBD2 本体のローダーに安全にフォールバック
        }

        if (targetFiles.isEmpty()) {
            ci.cancel();
            return;
        }

        // ファイルが1件だけの場合は並列化オーバーヘッドを避けて直接パース
        if (targetFiles.size() == 1) {
            File f = targetFiles.get(0);
            CompoundTag tag = parseNbtSafe(f);
            if (tag != null) {
                try {
                    consumer.accept(f, tag);
                } catch (Throwable t) {
                    LOGGER.error("[MBD2ParallelLoader] Error registering single MBD2 definition from file: {}", f.getName(), t);
                }
            }
            ci.cancel();
            return;
        }

        long start = System.currentTimeMillis();
        int parallelism = getPool().getParallelism();
        LOGGER.info("[MBD2ParallelLoader] 🚀 Multi-core parallelizing {} MBD2 '{}' files on {} threads...",
                targetFiles.size(), suffix, parallelism);

        // 2. ディスク I/O ＆ NBT バイナリデコード（最重量フェーズ）を全コアで完全並列実行
        Map<File, CompoundTag> parsedResults = new ConcurrentHashMap<>();

        try {
            com.fastlaunch.core.FastLaunchThreadHelper.executeParallel(targetFiles, file -> {
                CompoundTag tag = parseNbtSafe(file);
                if (tag != null) {
                    parsedResults.put(file, tag);
                }
            });
        } catch (Throwable t) {
            LOGGER.error("[MBD2ParallelLoader] Error during parallel NBT parsing, falling back to native loader: {}", dir, t);
            return; // フォールバック
        }

        // 3. 呼び出し元スレッドで安全かつ決定論的に consumer.accept を実行
        // （HashSet やイベントリスナーの非スレッドセーフティ、ID/順序競合を完全防止）
        int successfulCount = 0;
        for (File f : targetFiles) {
            CompoundTag tag = parsedResults.get(f);
            if (tag != null) {
                try {
                    consumer.accept(f, tag);
                    successfulCount++;
                } catch (Throwable t) {
                    LOGGER.error("[MBD2ParallelLoader] Error registering MBD2 definition from file: {}", f.getName(), t);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("[MBD2ParallelLoader] ✅ Completed {}/{} definitions in {} ms across {} threads.",
                successfulCount, targetFiles.size(), elapsed, parallelism);

        FastLaunchSuccessLogger.recordActiveFeature(
                "MBD2-ParallelNBTLoader",
                String.format("ACTIVE [Parsed %d/%d NBT files in %d ms on %d threads]",
                        successfulCount, targetFiles.size(), elapsed, parallelism)
        );

        ci.cancel();
    }

    /**
     * 非圧縮 NBT（MBD2 標準）および GZIP 圧縮 NBT の両方に対応した安全な NBT 読み込み。
     */
    private static CompoundTag parseNbtSafe(File file) {
        // 1. MBD2 の標準形式（非圧縮 NBT, NbtIo.read）でパース試行
        try {
            CompoundTag tag = NbtIo.read(file);
            if (tag != null) return tag;
        } catch (Exception e1) {
            // 2. 万が一 GZIP 圧縮されていた場合のフォールバック試行
            try {
                CompoundTag tag = NbtIo.readCompressed(file);
                if (tag != null) return tag;
            } catch (Exception e2) {
                LOGGER.warn("[MBD2ParallelLoader] Failed to parse NBT file (tried both uncompressed and compressed): {} (reason: {})",
                        file.getAbsolutePath(), e1.getMessage());
            }
        }
        return null;
    }
}
