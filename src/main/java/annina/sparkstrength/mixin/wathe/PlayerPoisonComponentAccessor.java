package annina.sparkstrength.mixin.wathe;

import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exact Adapter for Wathe's private {@code PlayerPoisonComponent.player}: the antidote mixin only sees the poison
 * component when NoellesRoles reads {@code poisonTicks}, and needs its owner to check for the Pathogen's virus.
 * Wathe 私有 {@code PlayerPoisonComponent.player} 的精确 Adapter：解毒剂 mixin 在 NoellesRoles 读取 {@code poisonTicks}
 * 时只拿到毒组件，需要其主人来判断病原体病毒。
 */
@Mixin(value = PlayerPoisonComponent.class, remap = false)
public interface PlayerPoisonComponentAccessor {
    @Accessor(value = "player", remap = false)
    PlayerEntity sparkstrength$getPlayer();
}
