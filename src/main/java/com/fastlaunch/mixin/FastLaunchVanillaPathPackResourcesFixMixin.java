package com.fastlaunch.mixin;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * バニラ PathPackResources においても空パス走査時の例外や不要な decomposePath 処理を抑止する Mixin。
 */
@Mixin(net.minecraft.server.packs.PathPackResources.class)
public abstract class FastLaunchVanillaPathPackResourcesFixMixin {

    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true, require = 0)
    private void onVanillaListResourcesHead(PackType type, String namespace, String path, PackResources.ResourceOutput resourceOutput, CallbackInfo ci) {
        if (path == null || path.isEmpty()) {
            ci.cancel();
        }
    }
}
