package annina.sparkstrength.client.mixin.bandit;

import annina.sparkstrength.client.role.bandit.BanditRevolverClient;
import annina.sparkstrength.item.BanditRevolverItem;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Bandit's moddable revolver uses Wathe's gun crosshair, lit when a shot would hit: a round is chambered, the gun is
 * off cooldown and a player stands within the fitted barrel's reach.
 * 强盗改装左轮使用 Wathe 的枪械准心，在这一枪能命中时点亮：已有上膛子弹、不在冷却中，且所装枪管的射程内有玩家。
 */
@Mixin(CrosshairRenderer.class)
public abstract class BanditRevolverCrosshairMixin {
    @Unique private static final Identifier NORMAL = Identifier.of("wathe", "hud/crosshair");
    @Unique private static final Identifier TARGET = Identifier.of("wathe", "hud/crosshair_target");

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$renderBanditRevolverCrosshair(MinecraftClient client, ClientPlayerEntity player,
                                                                      DrawContext context, RenderTickCounter tickCounter,
                                                                      CallbackInfo ci) {
        ItemStack stack = player.getMainHandStack();
        if (!client.options.getPerspective().isFirstPerson() || !BanditRevolverItem.isRevolver(stack)) {
            return;
        }
        ci.cancel();
        boolean hit = !player.getItemCooldownManager().isCoolingDown(stack.getItem())
                && BanditRevolverClient.findTarget(player, stack) != null;
        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2F, context.getScaledWindowHeight() / 2F, 0.0F);
        context.getMatrices().translate(-1.5F, -1.5F, 0.0F);
        context.drawGuiTexture(hit ? TARGET : NORMAL, 0, 0, 3, 3);
        context.getMatrices().pop();
    }
}
