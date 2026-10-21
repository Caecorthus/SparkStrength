package annina.sparkstrength.client.role.partyanimal;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.partyanimal.PartygoerCorpseComponent;
import annina.sparkstrength.role.partyanimal.PartygoerCorpseRules;
import dev.doctor4t.wathe.api.event.CanSeeBodyRole;
import dev.doctor4t.wathe.api.event.CanTargetBody;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * How a Partygoer corpse looks on every client: the Partygoer skin on the wide (classic-arm) body model, and, when
 * looked at, the name 派对客 plus the death reason for everyone, since it is obviously not a passenger.
 * 派对客尸体在所有客户端上的样子：宽手臂（经典）尸体模型配派对客皮肤；注视时对所有人显示名字“派对客”与死因，
 * 因为它显然不是乘客。
 *
 * <p>Wathe's {@code RoleNameRenderer} draws nothing for such a corpse (its owner has no role), so
 * {@code PartygoerCorpseLabelMixin} calls {@link #renderLabel} after it, with the same look-at ray, reach, darkness
 * gate, fade and layout. Viewers who already see full body info (spectators, the Coroner via {@code CanSeeBodyRole})
 * also get the time since death, like Wathe's line; everyone else gets the reason alone.
 * Wathe 的 {@code RoleNameRenderer} 对这种尸体什么也不画（主人没有身份），因此 {@code PartygoerCorpseLabelMixin} 在它之后调用
 * {@link #renderLabel}，使用相同的注视射线、距离、黑暗门槛、淡入淡出与排版。本就能看到完整尸体信息的观察者（旁观者、通过
 * {@code CanSeeBodyRole} 的验尸官）与 Wathe 一样额外看到死亡时长；其他人只看到死因。</p>
 */
public final class PartygoerCorpseClient {
    public static final Identifier TEXTURE = SparkStrength.id("textures/entity/partygoer.png");
    private static final SkinTextures SKIN =
            new SkinTextures(TEXTURE, null, null, null, SkinTextures.Model.WIDE, false);
    private static final String NAME_KEY = "entity.sparkstrength.partygoer";
    private static final float MIN_ALPHA = 0.05F;

    private static float alpha;
    private static @Nullable PlayerBodyEntity target;
    private static boolean fullBodyInfo;

    private PartygoerCorpseClient() {
    }

    /** Render thread: the Partygoer skin for a marked corpse, else {@code null}. / 渲染线程。 */
    public static @Nullable SkinTextures skinFor(PlayerBodyEntity body) {
        return PartygoerCorpseComponent.isPartygoerCorpse(body) ? SKIN : null;
    }

    /** Render thread, from the end of Wathe's {@code RoleNameRenderer.renderHud}. / 渲染线程。 */
    public static void renderLabel(TextRenderer renderer, ClientPlayerEntity player, DrawContext context,
                                   RenderTickCounter tickCounter) {
        float step = tickCounter.getTickDelta(true) / 4;
        boolean spectatorInformation = WatheClient.canSeeSpectatorInformation();
        if (ProjectileUtil.getCollision(player,
                entity -> entity instanceof PlayerBodyEntity body && CanTargetBody.EVENT.invoker().canTarget(player, body),
                PartygoerCorpseRules.labelRange(spectatorInformation)) instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerBodyEntity body
                && PartygoerCorpseComponent.isPartygoerCorpse(body)) {
            target = body;
            fullBodyInfo = spectatorInformation || CanSeeBodyRole.EVENT.invoker().canSee(player);
            alpha = MathHelper.lerp(step, alpha, 1.0F);
        } else {
            alpha = MathHelper.lerp(step, alpha, 0.0F);
        }

        PlayerBodyEntity body = target;
        if (body == null || body.isRemoved() || body.getWorld() != player.getWorld()) {
            // Eaten, bagged, unloaded or another world: drop the label at once. / 被吃、装袋、卸载或换了世界：立即撤下标签。
            target = null;
            alpha = 0.0F;
            return;
        }
        if (alpha <= MIN_ALPHA) {
            // Faded out: forget the corpse so no old world stays referenced. / 已淡出：忘掉这具尸体，不再引用旧世界。
            target = null;
            return;
        }

        int alphaBits = (int) (alpha * 255.0F) << 24;
        Identifier reason = body.getDeathReason();
        Text reasonText = Text.translatable("death_reason." + reason.getNamespace() + "." + reason.getPath());
        Text deathLine = fullBodyInfo
                ? Text.translatable("hud.body.death_info", PartygoerCorpseRules.elapsedSeconds(
                        body.getWorld().getTime(), body.getDeathGameTime())).append(reasonText)
                : reasonText;
        Text name = Text.translatable(NAME_KEY);

        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2.0F, context.getScaledWindowHeight() / 2.0F + 6,
                0.0F);
        context.getMatrices().scale(0.6F, 0.6F, 1.0F);
        context.drawTextWithShadow(renderer, name, -renderer.getWidth(name) / 2, 0, 0xFFFFFF | alphaBits);
        context.drawTextWithShadow(renderer, deathLine, -renderer.getWidth(deathLine) / 2, 16, 0xFF0000 | alphaBits);
        context.getMatrices().pop();
    }
}
