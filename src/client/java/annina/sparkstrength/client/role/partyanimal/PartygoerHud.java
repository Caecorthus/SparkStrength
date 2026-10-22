package annina.sparkstrength.client.role.partyanimal;

import annina.sparkstrength.client.compat.SparkWitchSecondaryKeyCompat;
import annina.sparkstrength.component.partyanimal.PartyAnimalSkillComponent;
import annina.sparkstrength.network.partyanimal.PartygoerMenuC2SPacket;
import annina.sparkstrength.role.partyanimal.PartyAnimalRules;
import annina.sparkstrength.role.partyanimal.PartygoerState;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

import java.util.Objects;

/**
 * The Party Animal's Partygoer line, right-aligned on the second bottom-right row (row one is NoellesRoles' helium
 * tip): task progress, ready, cooldown, or what the live Partygoer is doing.
 * 派对狂的派对客状态行，右对齐画在右下角第二行（第一行是 NoellesRoles 的氦气提示）：任务进度、可用、冷却，或场上派对客正在做什么。
 */
public final class PartygoerHud {
    private static final int ROW_FROM_BOTTOM = 2;

    private PartygoerHud() {
    }

    public static void render(TextRenderer renderer, ClientPlayerEntity player, DrawContext context) {
        if (!PartygoerClientHooks.isKeyAvailable()
                || !ClientPlayNetworking.canSend(PartygoerMenuC2SPacket.ID)
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || SwallowedPlayerComponent.isPlayerSwallowed(player)) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!PartyAnimalRules.isPartyAnimal(role)) {
            return;
        }
        Text text = line(PartyAnimalSkillComponent.KEY.get(player));
        int x = context.getScaledWindowWidth() - renderer.getWidth(text);
        int y = context.getScaledWindowHeight() - renderer.fontHeight * ROW_FROM_BOTTOM;
        context.drawTextWithShadow(renderer, text, x, y, role.color());
    }

    private static Text line(PartyAnimalSkillComponent skill) {
        if (skill.hasActive()) {
            PartygoerState state = PartygoerState.fromWire(skill.getActiveState());
            String target = skill.getActiveTarget();
            int seconds = skill.getActiveSeconds();
            return switch (state) {
                case HUNT -> Text.translatable("hud.sparkstrength.partygoer.hunt", target, seconds);
                case CARRY -> Text.translatable("hud.sparkstrength.partygoer.carry", target);
                case EAT, RAGE_EAT -> Text.translatable("hud.sparkstrength.partygoer.eat", target, seconds);
                case RAGE_STUN, RAGE_CHASE -> Text.translatable("hud.sparkstrength.partygoer.rage", target, seconds);
                case SWALLOWED -> Text.translatable("hud.sparkstrength.partygoer.swallowed");
            };
        }
        if (!skill.isUnlocked()) {
            return Text.translatable("hud.sparkstrength.partygoer.tasks", skill.getCompletedTasks(),
                    PartyAnimalRules.UNLOCK_TASKS);
        }
        if (skill.getCooldownTicks() > 0) {
            return Text.translatable("hud.sparkstrength.partygoer.cooldown", Math.ceilDiv(skill.getCooldownTicks(), 20));
        }
        return Text.translatable("hud.sparkstrength.partygoer.ready", keyText(), PartyAnimalRules.SKILL_PRICE);
    }

    private static Text keyText() {
        return Objects.requireNonNullElseGet(SparkWitchSecondaryKeyCompat.boundKeyText(), () -> Text.literal("N"));
    }
}
