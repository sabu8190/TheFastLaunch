package com.fastlaunch.mixin;

import com.fastlaunch.FastLaunchForgeMod;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("RETURN"), require = 0)
    private void onTitleScreenInit(CallbackInfo ci) {
        FastLaunchForgeMod.onGameLoadComplete();
    }
}
