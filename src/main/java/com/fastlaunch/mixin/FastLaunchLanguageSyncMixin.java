package com.fastlaunch.mixin;

import com.fastlaunch.core.FastLaunchTranslationSyncBridge;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ClientLanguage.loadFrom 完了時に安全に同期シグナルを送信する Mixin。
 */
@Mixin(value = ClientLanguage.class, priority = 1000)
public abstract class FastLaunchLanguageSyncMixin {
    @Inject(method = "loadFrom", at = @At("RETURN"), require = 0)
    private static void onLoadFromReturn(CallbackInfo ci) {
        FastLaunchTranslationSyncBridge.triggerIndexRebuild("ClientLanguage.loadFrom");
    }
}
