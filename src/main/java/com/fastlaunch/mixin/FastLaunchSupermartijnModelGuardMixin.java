package com.fastlaunch.mixin;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * SuperMartijn642 Core Lib (Entangled 等) モデル上書き NPE/RuntimeException 完全防止ガード。
 * 特定モデル ('entangled:block#inventory' 等) がリロード時に一時的にマップに存在しない場合でも、
 * クラッシュを完全防止してフォールバックモデルで安全に起動を継続させる。
 */
@Pseudo
@Mixin(targets = "com.supermartijn642.core.registry.ClientRegistrationHandler", priority = 500)
public abstract class FastLaunchSupermartijnModelGuardMixin {
    private static final Logger LOGGER = LogManager.getLogger("FastLaunch/SupermartijnGuard");

    @Inject(method = "handleModelBakeEvent", at = @At("HEAD"), require = 0)
    private void onHandleModelBakeEventHead(ModelEvent.ModifyBakingResult event, CallbackInfo ci) {
        if (event == null || event.getModels() == null) return;
        Map<ResourceLocation, BakedModel> models = event.getModels();
        BakedModel missingModel = models.get(ModelBakery.MISSING_MODEL_LOCATION);

        // entangled:block#inventory が存在しない場合、missingModel で安全にプレースホルダー補完
        ResourceLocation entangledInventory = new ResourceLocation("entangled", "block#inventory");
        if (!models.containsKey(entangledInventory)) {
            if (missingModel != null) {
                models.put(entangledInventory, missingModel);
                LOGGER.info("[SupermartijnGuard] 🛡️ Secured 'entangled:block#inventory' with fallback model (Prevented crash).");
            }
        }
    }
}
