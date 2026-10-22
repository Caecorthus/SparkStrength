package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla greets a new rider with "Press Shift to dismount"; sneaking can't free anyone from the Partygoer, so a
 * grabbed player is told that instead.
 * 原版会给新骑乘者提示“按 Shift 下来”；潜行挣脱不了派对客，所以被抓住的玩家看到的是对应提示。
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PartygoerGrabbedMessageMixin {
    @ModifyArg(
            method = "onEntityPassengersSet",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;setOverlayMessage(Lnet/minecraft/text/Text;Z)V"),
            index = 0
    )
    private Text sparkstrength$grabbedMessage(Text original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && PartygoerCarry.isCarried(client.player)) {
            return Text.translatable("tip.sparkstrength.partygoer.grabbed").formatted(Formatting.LIGHT_PURPLE);
        }
        return original;
    }
}
