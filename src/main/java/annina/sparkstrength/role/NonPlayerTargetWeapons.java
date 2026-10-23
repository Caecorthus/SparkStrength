package annina.sparkstrength.role;

import annina.sparkstrength.role.coroner.CoronerService;
import annina.sparkstrength.role.veteran.VeteranRules;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.shadowjester.ShadowJesterPlayerComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Knife-stab checks for the non-player targets our hooks resolve before NoellesRoles sees the stab (Bomber drones,
 * Partygoers): which hand holds the knife, NoellesRoles' Shadow Jester gate, and the cooldown a real stab pays.
 * 我们的钩子先于 NoellesRoles 处理的非玩家目标（炸弹客无人机、派对客）的刀刺检查：哪只手持刀、NoellesRoles 的影子小丑门槛，
 * 以及真实刺杀需付出的冷却。
 */
public final class NonPlayerTargetWeapons {
    private NonPlayerTargetWeapons() {
    }

    /** The hand holding a Wathe knife, main hand first, or null. / 持 Wathe 刀的手（主手优先），没有则为 null。 */
    public static @Nullable Hand knifeHand(ServerPlayerEntity attacker) {
        return attacker.getMainHandStack().getItem() instanceof KnifeItem ? Hand.MAIN_HAND
                : attacker.getOffHandStack().getItem() instanceof KnifeItem ? Hand.OFF_HAND : null;
    }

    /**
     * Mirrors NoellesRoles' ShadowJesterKnifeMixin (a higher-priority HEAD handler our stab hooks run before): a Shadow
     * Jester without the real knife may only stab its duel partner, which a drone or a Partygoer never is, and a
     * betrayal trophy holder's stabs never work. Re-check against NoellesRoles when it changes that mixin.
     * 镜像 NoellesRoles 的 ShadowJesterKnifeMixin（优先级更高的 HEAD 处理，我们的刀刺钩子先于它执行）：没有真刀的影子小丑只能刺其
     * 决斗搭档（无人机与派对客永远不是），背叛战利品持有者的刺击永远无效。NoellesRoles 修改该 mixin 时需同步复核。
     */
    public static boolean noellesRolesBlocksStab(ServerPlayerEntity attacker) {
        ShadowJesterPlayerComponent shadow = ShadowJesterPlayerComponent.KEY.get(attacker);
        if (GameWorldComponent.KEY.get(attacker.getWorld()).isRole(attacker, Noellesroles.SHADOW_JESTER)) {
            return !shadow.isRealKnife();
        }
        return shadow.isBetrayalTrophy();
    }

    /**
     * The knife cooldown of a real stab ({@link KnifeStabCooldown#TICKS}); none in creative or Loose Ends, for the
     * Veteran, or for an instant-knife Coroner disguise (whose stabs never set one). / 真实刺杀的刀冷却
     * （{@link KnifeStabCooldown#TICKS}）；创造模式、Loose Ends、老兵与验尸官瞬刀伪装没有冷却（它们的刺杀本就不设冷却）。
     */
    public static void applyKnifeStabCooldown(ServerPlayerEntity attacker, ItemStack knife) {
        GameWorldComponent game = GameWorldComponent.KEY.get(attacker.getWorld());
        if (attacker.isCreative() || game.getGameMode() == WatheGameModes.LOOSE_ENDS
                || VeteranRules.isVeteran(game.getRole(attacker))
                || CoronerService.hasInstantSilentKnifeDisguise(attacker)) {
            return;
        }
        attacker.getItemCooldownManager().set(knife.getItem(), KnifeStabCooldown.TICKS);
    }
}
