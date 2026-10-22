package annina.sparkstrength.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sneaking does not get a player out of the Partygoer's arms; only the Partygoer lets go.
 * 潜行无法挣脱派对客的双臂；只有派对客自己会放手。
 */
@Mixin(PlayerEntity.class)
public abstract class PartygoerCarriedDismountMixin {
    @ModifyReturnValue(method = "shouldDismount", at = @At("RETURN"))
    private boolean sparkstrength$heldTight(boolean original) {
        return original && !PartygoerCarry.isCarried((PlayerEntity) (Object) this);
    }
}
