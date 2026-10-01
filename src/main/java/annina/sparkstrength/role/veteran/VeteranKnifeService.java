package annina.sparkstrength.role.veteran;

import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.veteran.VeteranKnifeComponent;
import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerVeteranComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 老兵匕首的服务端规则。
 *
 * <p>这个服务由 mixin 在 {@link KnifeStabPayload.Receiver#receive} 开头调用。
 * 如果发包者是老兵，就由这里完整接管刺杀并取消 Wathe 原逻辑，从而同时做到：
 * 无刺杀音效、刺杀后无刀 CD、支持多把商店匕首累计次数，以及避免原版 0/1/2 次组件吞掉新买的刀。</p>
 */
public final class VeteranKnifeService {
    private VeteranKnifeService() {
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        VeteranKnifeComponent knife = VeteranKnifeComponent.KEY.get(player);
        if (VeteranRules.isVeteran(role)) {
            knife.initializeStartingKnife();
            syncWatheVeteranGuard(player, knife);
        } else {
            knife.reset();
            PlayerVeteranComponent.KEY.get(player).reset();
        }
    }

    public static void reset(ServerPlayerEntity player) {
        VeteranKnifeComponent.KEY.get(player).reset();
        PlayerVeteranComponent.KEY.get(player).reset();
    }

    /**
     * @return true 表示本次 payload 已由老兵逻辑处理，原 Wathe 刀人逻辑必须取消。
     */
    public static boolean handleKnifeStab(KnifeStabPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        if (!VeteranRules.isVeteran(role) && CoronerService.hasInstantSilentKnifeDisguise(player)) {
            return handleCoronerInstantSilentKnifeDisguise(payload, player, game);
        }
        if (!VeteranRules.isVeteran(role)) {
            return false;
        }

        // 只要是老兵的刀包，就不再放回原逻辑；无效目标直接整刀无效，避免原版播放声音/设置 CD。
        if (SparkTraitsCompat.isKillerInteractionBlocked(player) || player.isSpectator()) {
            return true;
        }
        Entity targetEntity = player.getServerWorld().getEntityById(payload.target());
        if (!(targetEntity instanceof ServerPlayerEntity target)
                || target.isSpectator()
                || target.distanceTo(player) > 3.0D) {
            return true;
        }
        if (!isHoldingKnife(player)) {
            return true;
        }

        VeteranKnifeComponent knife = VeteranKnifeComponent.KEY.get(player);
        adoptExistingWatheUsesIfNeeded(player, knife);
        if (!knife.hasStabUsesLeft()) {
            syncWatheVeteranGuard(player, knife);
            return true;
        }

        Hand usedHand = heldKnifeHand(player);
        if (!SparkFactionCompat.canAffectPlayer(player, target, GameConstants.DeathReasons.KNIFE)
                || SparkTraitsCompat.shouldCancelMeleeAttack(player, target, player.getStackInHand(usedHand))) {
            return true;
        }
        knife.useStab();
        if (VeteranRules.shouldRemoveKnifeAfterUse(knife.getStabUsesLeft())) {
            removeOneHeldOrInventoryKnife(player);
        }
        syncWatheVeteranGuard(player, knife);

        // 保留 Wathe 的回放记录：之后复盘仍能看到老兵使用了刀。
        GameRecordManager.recordItemUse(
                player,
                Registries.ITEM.getId(WatheItems.KNIFE),
                target,
                null
        );

        InnocentKnifeKillRules.PreKill preKill = captureInnocentKnifeKill(game, player, target);
        GameFunctions.killPlayer(target, true, player, GameConstants.DeathReasons.KNIFE);
        punishVeteranForStabbingInnocent(game, player, target, preKill);
        player.swingHand(usedHand);
        // 老兵加强要求刺杀后没有刀 CD；这里显式清掉，防止其他逻辑在同 tick 写入冷却。
        player.getItemCooldownManager().remove(WatheItems.KNIFE);
        return true;
    }

    private static boolean handleCoronerInstantSilentKnifeDisguise(
            KnifeStabPayload payload,
            ServerPlayerEntity player,
            GameWorldComponent game
    ) {
        if (SparkTraitsCompat.isKillerInteractionBlocked(player) || player.isSpectator()) {
            return true;
        }
        Entity targetEntity = player.getServerWorld().getEntityById(payload.target());
        if (!(targetEntity instanceof ServerPlayerEntity target)
                || target.isSpectator()
                || target.distanceTo(player) > 3.0D) {
            return true;
        }
        if (!isHoldingKnife(player)) {
            return true;
        }

        /*
         * 验尸官只从“老兵/清道夫尸体身份”借到无蓄力、无声音出刀，
         * 不接入老兵 2 次次数池；老兵尸体身份仍继承老兵“小脑惩罚”；
         * 清道夫尸体身份不继承老兵规则，但它是杀手阵营尸体，改由“杀手尸体借刀”小脑规则覆盖
         * （两者共用 InnocentKnifeKillRules，且尸体身份互斥，不会重复惩罚）；
         * 临时匕首会在解除/切换变形时统一回收。
         * The Veteran body inherits the Veteran knife penalty; the Scavenger body does not, but as a killer-faction
         * body it is covered by the Coroner killer-body knife rule (same shared rule, mutually exclusive bodies).
         */
        Hand usedHand = heldKnifeHand(player);
        if (!SparkFactionCompat.canAffectPlayer(player, target, GameConstants.DeathReasons.KNIFE)
                || SparkTraitsCompat.shouldCancelMeleeAttack(player, target, player.getStackInHand(usedHand))) {
            return true;
        }
        GameRecordManager.recordItemUse(
                player,
                Registries.ITEM.getId(WatheItems.KNIFE),
                target,
                null
        );
        InnocentKnifeKillRules.PreKill preKill = CoronerService.hasVeteranDisguise(player)
                ? captureInnocentKnifeKill(game, player, target)
                : captureCoronerKillerDisguiseKnifeKill(player, target, GameConstants.DeathReasons.KNIFE);
        GameFunctions.killPlayer(target, true, player, GameConstants.DeathReasons.KNIFE);
        punishVeteranForStabbingInnocent(game, player, target, preKill);
        player.swingHand(usedHand);
        player.getItemCooldownManager().remove(WatheItems.KNIFE);
        return true;
    }

    public static void addPurchasedKnife(ServerPlayerEntity player) {
        VeteranKnifeComponent knife = VeteranKnifeComponent.KEY.get(player);
        knife.addKnife();
        syncWatheVeteranGuard(player, knife);
    }

    /**
     * Coroner killer-body knife rule for stab paths outside this service (Wathe's plain knife receiver and the
     * Silencer body). A Coroner is a Wathe civilian: a killer-faction corpse disguise lends the knife, not the killer
     * faction. Call BEFORE {@code GameFunctions.killPlayer}; null means the rule does not apply (not a Coroner, no
     * killer-faction disguise, or not a knife death). Veteran bodies are civilian and never match here.
     * 验尸官杀手尸体借刀规则入口，供本服务以外的刺杀路径使用（Wathe 普通刀包、静语者尸体）。验尸官是 Wathe 好人：
     * 杀手阵营尸体伪装只借出匕首，不借出杀手阵营。须在 killPlayer 之前调用；返回 null 表示不适用
     * （不是验尸官、没有杀手阵营伪装或不是匕首死亡）。老兵尸体属于好人阵营，永远不会在这里命中。
     */
    public static @Nullable InnocentKnifeKillRules.PreKill captureCoronerKillerDisguiseKnifeKill(
            @Nullable ServerPlayerEntity attacker,
            @Nullable ServerPlayerEntity target,
            Identifier deathReason
    ) {
        if (attacker == null
                || target == null
                || attacker == target
                || !GameConstants.DeathReasons.KNIFE.equals(deathReason)
                || !CoronerService.isActualCoroner(attacker)
                || !CoronerService.hasKillerFactionDisguise(attacker)) {
            return null;
        }
        return captureInnocentKnifeKill(GameWorldComponent.KEY.get(attacker.getWorld()), attacker, target);
    }

    /**
     * Applies the captured Coroner killer-body knife rule right after the stab's kill returns (same timing as the
     * Veteran knife); a null snapshot is a no-op.
     * 在刺杀击杀返回后立即结算已记录的验尸官杀手尸体借刀规则（与老兵匕首时序一致）；快照为 null 时不做任何事。
     */
    public static void punishCoronerKillerDisguiseKnifeKill(
            @Nullable ServerPlayerEntity attacker,
            @Nullable ServerPlayerEntity target,
            @Nullable InnocentKnifeKillRules.PreKill preKill
    ) {
        if (attacker == null || target == null || preKill == null) {
            return;
        }
        punishVeteranForStabbingInnocent(GameWorldComponent.KEY.get(attacker.getWorld()), attacker, target, preKill);
    }

    private static InnocentKnifeKillRules.PreKill captureInnocentKnifeKill(
            GameWorldComponent game,
            ServerPlayerEntity attacker,
            ServerPlayerEntity target
    ) {
        // Read before killPlayer: SparkTraits clears the victim's active traits in its KillPlayer.AFTER listener.
        // 必须在 killPlayer 之前读取：SparkTraits 会在 KillPlayer.AFTER 监听中清空死者的生效天赋。
        return InnocentKnifeKillRules.beforeKill(
                game.isInnocent(target),
                SparkTraitsCompat.hasImpostor(target),
                SparkTraitsCompat.hasConscience(target),
                SparkTraitsCompat.hasImpostor(attacker)
        );
    }

    private static void punishVeteranForStabbingInnocent(
            GameWorldComponent game,
            ServerPlayerEntity veteran,
            ServerPlayerEntity target,
            @Nullable InnocentKnifeKillRules.PreKill preKill
    ) {
        if (!InnocentKnifeKillRules.shouldPunish(
                preKill,
                game.isPlayerDead(target.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(veteran))) {
            return;
        }

        // 需求指定“使用匕首 knife 杀到好人阵营时老兵小脑死亡”；好人阵营按 InnocentKnifeKillRules 的有效阵营判定。
        // 先确认目标已死亡，避免疯魔盾或其它 KillPlayer.BEFORE 取消死亡时误罚老兵。
        // 此服务只接管 Wathe knife 的刺杀包，所以这里天然只覆盖明确使用匕首的成功击杀。
        // SparkTraits 对真老兵刀杀善良杀手的特判可能已在 AFTER 中先处死老兵，此时上面的存活检查会跳过，不会重复击杀。
        GameFunctions.killPlayer(veteran, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
    }

    private static void adoptExistingWatheUsesIfNeeded(ServerPlayerEntity player, VeteranKnifeComponent knife) {
        if (knife.hasStabUsesLeft()) {
            return;
        }
        PlayerVeteranComponent watheVeteran = PlayerVeteranComponent.KEY.get(player);
        if (watheVeteran.hasStabUsesLeft()) {
            // 兼容中途加载/旧局状态：如果 Wathe 原组件还有开局刀次数，而我们还没记录，就接管过来。
            knife.addStabUses(watheVeteran.getStabUsesLeft());
        }
    }

    private static void syncWatheVeteranGuard(ServerPlayerEntity player, VeteranKnifeComponent knife) {
        PlayerVeteranComponent watheVeteran = PlayerVeteranComponent.KEY.get(player);
        if (knife.hasStabUsesLeft()) {
            // 原组件最多只能记 2 次，这里只把它当作“老兵仍有可用刀，不能捡枪”的布尔标记。
            watheVeteran.initialize();
        } else {
            watheVeteran.reset();
        }
    }

    private static boolean isHoldingKnife(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(WatheItems.KNIFE)
                || player.getOffHandStack().isOf(WatheItems.KNIFE);
    }

    private static Hand heldKnifeHand(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(WatheItems.KNIFE) ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    private static void removeOneHeldOrInventoryKnife(ServerPlayerEntity player) {
        if (removeOneKnifeFromStack(player.getMainHandStack())) {
            return;
        }
        if (removeOneKnifeFromStack(player.getOffHandStack())) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(WatheItems.KNIFE)) {
                player.getInventory().removeStack(slot, 1);
                return;
            }
        }
    }

    private static boolean removeOneKnifeFromStack(ItemStack stack) {
        if (!stack.isOf(WatheItems.KNIFE)) {
            return false;
        }
        stack.decrement(1);
        return true;
    }
}
