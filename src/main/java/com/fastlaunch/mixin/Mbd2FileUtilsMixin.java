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
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;

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

        File[] files = dir.listFiles((d, name) -> name.endsWith(suffix));
        if (files == null || files.length == 0) return;

        System.out.println("[FastLaunch] >>> MBD2 Multi-Core Parallel Loader TRIGGERED for " + files.length + " files! <<<");
        LOGGER.info("[MBD2ParallelLoader] >>> Multi-core parallelizing {} MBD2 files on {} cores! <<<",
                files.length, getPool().getParallelism());

        long start = System.currentTimeMillis();

        com.fastlaunch.core.FastLaunchThreadHelper.executeParallel(Arrays.asList(files), file -> {
            try {
                CompoundTag tag = NbtIo.readCompressed(file);
                if (tag != null) {
                    synchronized (consumer) {
                        consumer.accept(file, tag);
                    }
                }
            } catch (Exception ignored) {}
        });

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[FastLaunch] >>> MBD2 Multi-Core Parallel Loader COMPLETED in " + elapsed + " ms! <<<");
        LOGGER.info("[MBD2ParallelLoader] Completed {} definitions in {} ms across {} threads.", files.length, elapsed, getPool().getParallelism());
        FastLaunchSuccessLogger.recordActiveFeature(
                "MBD2-ParallelNBTLoader", 
                String.format("ACTIVE [Parsed %d NBT files in %d ms on %d threads]", files.length, elapsed, getPool().getParallelism())
        );

        ci.cancel();
    }
}
