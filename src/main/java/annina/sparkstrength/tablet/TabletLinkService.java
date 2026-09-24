package annina.sparkstrength.tablet;

import annina.sparkstrength.component.tablet.TabletWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Identity-link handshake for anonymous tablet channels (KILLER): an eligible holder Shift+right-clicks a player, an
 * eligible target gets a private 5 s action-bar prompt, and the target Shift+right-clicking back completes a pairwise,
 * round-long link that reveals both names to each other.
 * 匿名平板频道（杀手频道）的身份互认握手：合格持有者 Shift+右键 某玩家，合格目标收到仅自己可见的 5 秒动作栏提示，
 * 目标在时限内反向 Shift+右键 即完成成对、持续整局的互认，双方互相显示真实名字。
 *
 * <p>No-oracle rules: the initiator's client shows "link signal sent" locally for every linkable target and the server
 * stays silent unless the link completes or already exists, so the gesture never reveals membership. Only eligible
 * targets are prompted. No swing, no world sound, no broadcast (wathe proximity voice would carry any sound).
 * 无预言机规则：发起者客户端对所有可互认目标都在本地显示“已发出互认信号”，服务端除互认完成或已存在外一律不回应，
 * 因此该操作无法探测成员身份。只有合格目标会收到提示。不挥手、不播放世界音效、不广播（wathe 近距离语音会传出任何声音）。</p>
 */
public final class TabletLinkService {
    private static final int COUNTDOWN_INTERVAL_TICKS = 20;

    private TabletLinkService() {
    }

    /**
     * Side-agnostic: the client uses it to decide CONSUME vs opening the tablet, the server re-checks it.
     * 与端无关：客户端据此决定是否消费本次点击，服务端会再次校验。
     */
    public static boolean isEligibleActor(PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && TabletLinkRules.hasAnonymousChannel(TabletChannelResolver.identityChannels(player));
    }

    /**
     * Side-agnostic and based on entity-synced state only, so a hidden player (invisible, spectator, Morphling corpse)
     * behaves like clicking air on both sides and the "sent" line never exposes them.
     * 与端无关，仅依赖实体同步状态：隐藏的玩家（隐身、旁观、变形者尸体模式）在两端都等同于对空气右键，
     * “已发出”提示因此不会暴露他们。
     */
    public static boolean isLinkableTarget(PlayerEntity target) {
        return target != null && !target.isInvisible() && !target.isSpectator() && !isMorphlingCorpse(target);
    }

    public static void handleLinkGesture(ServerPlayerEntity actor, ServerPlayerEntity target) {
        if (actor == null || target == null || actor == target || actor.getServerWorld() != target.getServerWorld()) {
            return;
        }
        // Silent on failure: the client already showed "sent", identical for every target.
        // 失败时保持沉默：客户端已显示“已发出”，对所有目标都一致。
        if (!isEligibleActor(actor) || !TabletAccess.hasTabletInHotbar(actor) || !isLinkableTarget(target)) {
            return;
        }
        ServerWorld world = actor.getServerWorld();
        long now = world.getTime();
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        UUID actorUuid = actor.getUuid();
        UUID targetUuid = target.getUuid();
        // Held use re-fires every 4 ticks; such repeats must not replace the "linked" confirmation.
        // 按住使用键每 4 tick 重复触发；这些重复不能覆盖“已互认”的确认提示。
        boolean heldRepeat = tablet.recordLinkGesture(actorUuid, targetUuid, now);
        boolean targetEligible = GameFunctions.isPlayerPlayingAndAlive(target)
                && TabletAccess.hasTabletInHotbar(target)
                && TabletLinkRules.canLink(
                TabletChannelResolver.identityChannels(actor),
                TabletChannelResolver.identityChannels(target)
        );
        boolean reverseLive = isLive(tablet.linkRequest(targetUuid, actorUuid), now);
        boolean forwardLive = isLive(tablet.linkRequest(actorUuid, targetUuid), now);

        switch (TabletLinkRules.decide(tablet.isLinked(actorUuid, targetUuid), reverseLive, targetEligible, forwardLive)) {
            case ALREADY_LINKED -> {
                if (!heldRepeat) {
                    overlay(actor, "message.sparkstrength.tablet.link.already", name(target));
                }
            }
            case COMPLETE -> {
                tablet.link(actorUuid, targetUuid);
                tablet.removeLinkRequest(actorUuid, targetUuid);
                tablet.removeLinkRequest(targetUuid, actorUuid);
                overlay(actor, "message.sparkstrength.tablet.link.linked", name(target));
                overlay(target, "message.sparkstrength.tablet.link.linked", name(actor));
                TabletStateService.syncPair(actor, target);
            }
            case IGNORE_PENDING -> {
                // Held right-click re-fires every 4 ticks; keep the original window and prompt.
                // 按住右键每 4 tick 会重复触发；保持原有时限与提示。
            }
            case REQUEST -> {
                String actorName = name(actor);
                long expiryTick = now + TabletLinkRules.LINK_WINDOW_TICKS;
                // Stored for ineligible targets too (without a prompt): repeat clicks take the same IGNORE_PENDING path.
                // 不合格目标同样存入请求（但不提示）：重复点击走同样的 IGNORE_PENDING 路径。
                tablet.putLinkRequest(new TabletWorldComponent.LinkRequest(
                        actorUuid, targetUuid, expiryTick, targetEligible, actorName));
                if (targetEligible) {
                    // Include every live request to this target so a new click never hides an older requester.
                    // 包含发往该目标的所有有效请求，避免新的请求遮住更早的请求者。
                    promptTarget(target, liveNotifyingRequestsTo(tablet, targetUuid, now), now);
                }
            }
        }
    }

    /**
     * Prunes expired requests and refreshes each target's live countdown once per second. Requests are grouped per
     * target because the action bar holds one line: a single combined prompt names every live requester, and "expired"
     * is only shown when no other live request to that target remains.
     * 清理过期请求，并每秒刷新目标的倒计时。动作栏只有一行，因此按目标合并请求：一条提示列出所有有效请求者，
     * 且只有在该目标没有其他有效请求时才显示“已过期”。
     */
    public static void tick(ServerWorld world) {
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        long now = world.getTime();
        Set<UUID> expiredTargets = new HashSet<>();
        Map<UUID, List<TabletWorldComponent.LinkRequest>> liveByTarget = new LinkedHashMap<>();
        for (TabletWorldComponent.LinkRequest request : tablet.linkRequests()) {
            if (!TabletLinkRules.isLive(request.expiryTick(), now)) {
                tablet.removeLinkRequest(request.from(), request.to());
                if (request.notifyTarget()) {
                    expiredTargets.add(request.to());
                }
            } else if (request.notifyTarget()) {
                liveByTarget.computeIfAbsent(request.to(), ignored -> new ArrayList<>()).add(request);
            }
        }
        for (UUID targetUuid : expiredTargets) {
            if (!liveByTarget.containsKey(targetUuid)
                    && world.getPlayerByUuid(targetUuid) instanceof ServerPlayerEntity target) {
                overlay(target, "message.sparkstrength.tablet.link.expired");
            }
        }
        for (Map.Entry<UUID, List<TabletWorldComponent.LinkRequest>> entry : liveByTarget.entrySet()) {
            List<TabletWorldComponent.LinkRequest> requests = entry.getValue();
            boolean due = expiredTargets.contains(entry.getKey()) || requests.stream()
                    .anyMatch(request -> (request.expiryTick() - now) % COUNTDOWN_INTERVAL_TICKS == 0);
            if (due
                    && world.getPlayerByUuid(entry.getKey()) instanceof ServerPlayerEntity target
                    && GameFunctions.isPlayerPlayingAndAlive(target)
                    && TabletAccess.hasTabletInHotbar(target)) {
                // Action-bar overlays fade after 60 ticks; resending each second keeps the countdown live.
                // 动作栏提示 60 tick 后淡出；每秒重发以保持倒计时实时显示。
                promptTarget(target, requests, now);
            }
        }
    }

    private static boolean isLive(@Nullable TabletWorldComponent.LinkRequest request, long now) {
        return request != null && TabletLinkRules.isLive(request.expiryTick(), now);
    }

    private static List<TabletWorldComponent.LinkRequest> liveNotifyingRequestsTo(
            TabletWorldComponent tablet,
            UUID targetUuid,
            long now
    ) {
        List<TabletWorldComponent.LinkRequest> requests = new ArrayList<>();
        for (TabletWorldComponent.LinkRequest request : tablet.linkRequests()) {
            if (request.notifyTarget() && request.to().equals(targetUuid) && isLive(request, now)) {
                requests.add(request);
            }
        }
        return requests;
    }

    private static void promptTarget(ServerPlayerEntity target, List<TabletWorldComponent.LinkRequest> requests, long now) {
        if (requests.isEmpty()) {
            return;
        }
        if (requests.size() == 1) {
            TabletWorldComponent.LinkRequest request = requests.get(0);
            overlay(target, "message.sparkstrength.tablet.link.incoming",
                    request.fromName(), TabletLinkRules.remainingSeconds(request.expiryTick(), now));
            return;
        }
        List<TabletWorldComponent.LinkRequest> ordered = new ArrayList<>(requests);
        ordered.sort(Comparator.comparingLong(TabletWorldComponent.LinkRequest::expiryTick));
        MutableText names = Text.empty();
        for (int index = 0; index < ordered.size(); index++) {
            TabletWorldComponent.LinkRequest request = ordered.get(index);
            if (index > 0) {
                names.append(", ");
            }
            names.append(Text.translatable("message.sparkstrength.tablet.link.incoming_entry",
                    request.fromName(), TabletLinkRules.remainingSeconds(request.expiryTick(), now)));
        }
        overlay(target, "message.sparkstrength.tablet.link.incoming_multi", names);
    }

    /**
     * NoellesRoles syncs {@code corpseMode} to every client, so this reads the same on both sides.
     * NoellesRoles 会把 corpseMode 同步给所有客户端，因此两端读取结果一致。
     */
    private static boolean isMorphlingCorpse(PlayerEntity player) {
        MorphlingPlayerComponent morph = MorphlingPlayerComponent.KEY.getNullable(player);
        return morph != null && morph.corpseMode;
    }

    private static String name(PlayerEntity player) {
        return player.getName().getString();
    }

    private static void overlay(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Text.translatable(key, args), true);
    }
}
