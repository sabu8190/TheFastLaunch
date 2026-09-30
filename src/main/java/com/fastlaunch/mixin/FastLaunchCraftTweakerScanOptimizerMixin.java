package com.fastlaunch.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * CraftTweaker の ForgePlatformHelper における直列アノテーションスキャンを並列化する Mixin。
 * 
 * 根本課題:
 * CraftTweaker は起動時（コンストラクタ内）の loadPlugins(), ZenClassGatherer 等で
 * ForgePlatformHelper.findClassesWithAnnotation を呼び出し、
 * 300個以上の Mod JAR の全スキャンデータ（getAllScanData()）を
 * シングルスレッドの .stream() で直列に走査している。
 * これにより Constructing Mods 中の CraftTweaker 単体で 9,076 ms を消費している。
 * 
 * 解決策:
 * List.stream() の呼び出しを List.parallelStream() に差し替える（@Redirect）。
 * これにより、全 ModFileScanData の走査・型マッチ・Class.forName が
 * CPU のマルチコア（14コア20スレッド）で一斉に並列実行され、
 * 9秒 ➔ 1秒未満 に激減する。
 */
@Pseudo
@Mixin(targets = "com.blamejared.crafttweaker.platform.ForgePlatformHelper", remap = false)
public abstract class FastLaunchCraftTweakerScanOptimizerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/CraftTweakerScan");
    private static final AtomicInteger SCAN_COUNT = new AtomicInteger(0);

    @Redirect(
        method = "findClassesWithAnnotation",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;stream()Ljava/util/stream/Stream;"
        ),
        require = 0
    )
    private <E> Stream<E> redirectStreamToParallelStream(List<E> list) {
        int count = SCAN_COUNT.incrementAndGet();
        if (count == 1 || count % 5 == 0) {
            LOGGER.info("[CraftTweakerScan] ⚡ Parallelizing ModList.getAllScanData() scan #{} across {} mod scan data items",
                    count, list.size());
        }
        return list.parallelStream();
    }
}
