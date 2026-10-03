package annina.sparkstrength.client.mixin.attendant;

import annina.sparkstrength.client.role.attendant.flashlight.FlashlightLights;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lights entities caught in a flashlight beam. {@code EntityRenderer#getLight} is the single final entry every entity
 * renderer, the first-person hand and held items read their packed light from, so raising its block-light half here
 * covers players, corpses and items without touching individual renderers. Only the block light rises (to the
 * strongest unoccluded beam, never the holder's own); sky light and any brighter vanilla value are kept, so this
 * composes with other return-value modifiers. Client-only and visual: no gameplay state.
 * 让被手电光束照到的实体变亮。{@code EntityRenderer#getLight} 是所有实体渲染器、第一人称手部与手持物品读取打包光照的
 * 唯一 final 入口，在此提升方块光部分即可覆盖玩家、尸体与物品，而无需改动各个渲染器。只提升方块光（取最强的未遮挡
 * 光束，不含持有者自己的），保留天空光与更亮的原值，可与其他返回值修改共存。仅客户端视觉效果，不涉及玩法状态。
 */
@Mixin(EntityRenderer.class)
public abstract class FlashlightEntityLightMixin {
    @ModifyReturnValue(method = "getLight", at = @At("RETURN"))
    private int sparkstrength$applyFlashlightBlockLight(
            int packedLight, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) float tickDelta
    ) {
        int blockLight = LightmapTextureManager.getBlockLightCoordinates(packedLight);
        int boosted = FlashlightLights.boostBlockLight(entity, tickDelta, blockLight);
        if (boosted == blockLight) {
            return packedLight;
        }
        return LightmapTextureManager.pack(boosted, LightmapTextureManager.getSkyLightCoordinates(packedLight));
    }
}
