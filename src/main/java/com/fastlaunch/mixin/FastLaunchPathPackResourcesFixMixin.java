package com.fastlaunch.mixin;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge の PathPackResources において、空文字列 ("") などの空パス走査時に
 * FileUtil.decomposePath が失敗して大量の "Invalid path : Invalid path ''" エラーログが
 * スパム出力される（19万回以上）現象を完全に防止し、安全に抑止する Mixin。
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.resource.PathPackResources", remap = false)
public abstract class FastLaunchPathPackResourcesFixMixin {

    @Inject(method = {"listResources", "m_8031_"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onListResourcesHead(PackType type, String namespace, String path, PackResources.ResourceOutput resourceOutput, CallbackInfo ci) {
        if (path == null || path.isEmpty()) {
            // 空パス指定による不正ログ出力を完全に抑止して安全に終了
            ci.cancel();
        }
    }
}
