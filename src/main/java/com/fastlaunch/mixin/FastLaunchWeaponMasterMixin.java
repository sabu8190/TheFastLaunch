package com.fastlaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WeaponMaster Mod のコンストラクタ内で実行される
 * 6,425,544 通り（642万回）の武器バリアント直積総当たりループを即時バイパスする Mixin。
 * 
 * 単に LOGGER.info でログ出力するためだけに実行されている巨大ループを 0 ms 化し、
 * 初期化時間（17.0秒）を数十ミリ秒以下へ短縮します。
 */
@Pseudo
@Mixin(targets = "com.sky.weaponmaster.datas.Parts", remap = false)
public class FastLaunchWeaponMasterMixin {
    private static final long[] CACHED_VARIANTS = new long[]{6425544L};

    @Inject(method = "countEveryWeaponVarient", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onCountEveryWeaponVarient(CallbackInfoReturnable<long[]> cir) {
        System.out.println("[FastLaunch] \u26a1 WeaponMaster 6,425,544 combinations variant loop bypassed to 0 ms!");
        cir.setReturnValue(CACHED_VARIANTS);
    }
}
