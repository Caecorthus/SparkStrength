package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While carried by the Partygoer: no attack, use, pick, drop, hand swap, hotbar keys, nor the role skill keys (NR's
 * ability key and the shared second-skill key). Looking around and voice stay. The server refuses the same actions.
 * 被派对客扛着时：禁用攻击、使用、选取、丢弃、换手、快捷栏键，以及职业技能键（NR 技能键与共享的第二技能键）。转头与语音保留。服务端同样拒绝这些操作。
 */
@Mixin(value = KeyBinding.class, priority = 5000)
public abstract class PartygoerCarriedKeyMixin {
    @Unique
    private static final String SECONDARY_SKILL_SHARED = "key.sparkwitch.secondary_skill";
    @Unique
    private static final String SECONDARY_SKILL_FALLBACK = "key.sparkstrength.secondary_skill";

    @Inject(method = "wasPressed", at = @At("RETURN"), cancellable = true)
    private void sparkstrength$lockCarriedWasPressed(CallbackInfoReturnable<Boolean> cir) {
        sparkstrength$lockCarried(cir);
    }

    @Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
    private void sparkstrength$lockCarriedIsPressed(CallbackInfoReturnable<Boolean> cir) {
        sparkstrength$lockCarried(cir);
    }

    @Unique
    private void sparkstrength$lockCarried(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !PartygoerCarry.isCarried(client.player)) {
            return;
        }
        KeyBinding key = (KeyBinding) (Object) this;
        if (sparkstrength$isLocked(client.options, key)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static boolean sparkstrength$isLocked(GameOptions options, KeyBinding key) {
        if (key == options.attackKey || key == options.useKey || key == options.pickItemKey
                || key == options.dropKey || key == options.swapHandsKey || key == NoellesrolesClient.abilityBind) {
            return true;
        }
        for (KeyBinding hotbar : options.hotbarKeys) {
            if (key == hotbar) {
                return true;
            }
        }
        String id = key.getTranslationKey();
        return SECONDARY_SKILL_SHARED.equals(id) || SECONDARY_SKILL_FALLBACK.equals(id);
    }
}
