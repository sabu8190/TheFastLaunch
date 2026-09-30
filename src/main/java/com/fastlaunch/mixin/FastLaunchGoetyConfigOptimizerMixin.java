package com.fastlaunch.mixin;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Goety のコンストラクタにおける直列 TOML 読み込み（10,713 ms）を解消する Mixin。
 * 
 * 根本課題:
 * Goety はコンストラクタ内で 6 つの超巨大 TOML（合計 5,239 行）を
 * MainConfig, AttributesConfig, SpellConfig, BrewConfig, MobsConfig, ItemConfig の
 * loadConfig() を直列に 6 回呼び出して同期的にパースしている。
 * 
 * 解決策:
 * ClassPreloadEngine のバックグラウンド並列プールでゲーム起動最序盤に 6 ファイルを一斉並列ロードし、
 * 本番の loadConfig() が呼ばれた時点ですでに spec.isLoaded() == true であれば
 * 重複するディスク読み込み＆TOML構文解析をスキップ（0ms）する。
 */
@Pseudo
@Mixin(targets = {
    "com.Polarice3.Goety.config.MainConfig",
    "com.Polarice3.Goety.config.AttributesConfig",
    "com.Polarice3.Goety.config.SpellConfig",
    "com.Polarice3.Goety.config.BrewConfig",
    "com.Polarice3.Goety.config.MobsConfig",
    "com.Polarice3.Goety.config.ItemConfig"
}, remap = false)
public abstract class FastLaunchGoetyConfigOptimizerMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/GoetyConfig");

    @Inject(method = "loadConfig", at = @At("HEAD"), cancellable = true, require = 0)
    private static void onLoadConfig(ForgeConfigSpec spec, String path, CallbackInfo ci) {
        if (spec != null && spec.isLoaded()) {
            LOGGER.info("[GoetyConfig] ⚡ Skipping redundant disk TOML parsing for already loaded config: {}", path);
            ci.cancel();
        }
    }
}
