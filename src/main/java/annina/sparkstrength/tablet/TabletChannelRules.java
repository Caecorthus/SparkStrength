package annina.sparkstrength.tablet;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pure channel membership, selection, sending and switching rules.
 * 纯频道成员、选择、发送与切换规则。
 *
 * <p>Membership is identity-based: every channel in {@link #allowed(Facts)} is a network the holder belongs to
 * (push fan-out, member lists, police electorate). The selected channel only picks the chat/features being
 * viewed, so an Impostor switching views never appears in or disappears from either member list. The member-list
 * payload is additionally filtered per viewer ({@code TabletIdentityRules}): by link state on the killer channel, by
 * the viewer's completed tasks on the police channel.
 * 成员资格由身份决定：allowed 中的每个频道都是持有者所属的网络（推送范围、成员列表、义警选民）。
 * 所选频道只决定当前查看的聊天与功能，因此内鬼切换视图不会让自己在任何成员列表中出现或消失。
 * 下发的成员列表还会按查看者过滤（见 TabletIdentityRules）：杀手频道按互认状态，义警频道按查看者已完成的任务数。</p>
 */
public final class TabletChannelRules {
    public static final int SWITCH_COOLDOWN_TICKS = 100;
    public static final int CHAT_HISTORY_LIMIT = 50;

    private TabletChannelRules() {
    }

    /**
     * Identity facts gathered by the resolver; pure so the tablet grant, server access and tests share one truth table.
     * 解析器收集的身份事实；保持纯净，便于平板发放、服务端访问与测试共用同一真值表。
     *
     * @param hasRole      the holder has a non-empty round role / 持有者拥有有效的局内身份
     * @param policeRole   {@link TabletShopRules#isPoliceNetworkRole} / 义警网络身份
     * @param killerRole   wathe {@code Role.canUseKiller()} / 原生杀手标记
     * @param impostor     active SparkTraits Impostor / SparkTraits 内鬼天赋
     * @param conscience   active SparkTraits Conscience / SparkTraits 善良天赋
     * @param witchFaction SparkFactionAPI base faction {@code sparkwitch:witch} / 魔女阵营基础阵营
     * @param undercover   real NoellesRoles Undercover role {@link TabletShopRules#isUndercover} / 真实身份为 NoellesRoles 卧底
     */
    public record Facts(
            boolean hasRole,
            boolean policeRole,
            boolean killerRole,
            boolean impostor,
            boolean conscience,
            boolean witchFaction,
            boolean undercover
    ) {
    }

    public static EnumSet<TabletChannel> allowed(Facts facts) {
        if (facts == null || !facts.hasRole()) {
            return EnumSet.noneOf(TabletChannel.class);
        }
        if (facts.witchFaction()) {
            // SparkFactionAPI's base faction wins, e.g. a recruited Accomplice that kept an Impostor trait.
            // 以 SparkFactionAPI 基础阵营为准，例如保留内鬼天赋的被招募共犯。
            return EnumSet.of(TabletChannel.WITCH);
        }
        if (facts.undercover()) {
            // Undercover poses as a killer teammate: it joins the killer network, where names stay ??? until linked.
            // 卧底伪装成杀手队友：加入杀手网络，名字在互认前显示为 ???。
            return EnumSet.of(TabletChannel.KILLER);
        }
        if (facts.impostor()) {
            // Impostor is a killer-team member that may also infiltrate the police network.
            // 内鬼属于杀手阵营，同时可以潜入义警网络。
            return EnumSet.of(TabletChannel.POLICE, TabletChannel.KILLER);
        }
        if (facts.policeRole()) {
            return EnumSet.of(TabletChannel.POLICE);
        }
        if (facts.conscience()) {
            // Conscience killers secretly side with civilians; the killer network would expose teammates.
            // 善良杀手暗中站在好人一边；杀手网络会暴露队友。
            return EnumSet.noneOf(TabletChannel.class);
        }
        if (facts.killerRole()) {
            return EnumSet.of(TabletChannel.KILLER);
        }
        return EnumSet.noneOf(TabletChannel.class);
    }

    /**
     * Keeps a still-allowed selection, otherwise falls back to the first allowed channel in enum order.
     * 保留仍被允许的选择，否则回退到枚举顺序中第一个允许的频道。
     */
    public static @Nullable TabletChannel choose(Set<TabletChannel> allowed, @Nullable TabletChannel selected) {
        if (allowed == null || allowed.isEmpty()) {
            return null;
        }
        if (selected != null && allowed.contains(selected)) {
            return selected;
        }
        for (TabletChannel channel : TabletChannel.values()) {
            if (allowed.contains(channel)) {
                return channel;
            }
        }
        return null;
    }

    /** Dead holders may read every channel but never transmit. / 死亡持有者可阅读任何频道，但不能发送。 */
    public static boolean canSend(@Nullable TabletChannel channel, boolean alive) {
        return channel != null && alive;
    }

    public static SwitchResult canSwitch(
            Set<TabletChannel> allowed,
            @Nullable TabletChannel current,
            @Nullable TabletChannel target,
            boolean meetingActive,
            int cooldownTicksRemaining
    ) {
        if (target == null || allowed == null || !allowed.contains(target)) {
            return SwitchResult.DENIED;
        }
        if (target == current) {
            return SwitchResult.UNCHANGED;
        }
        // Joining the police view during a meeting is allowed so the holder can vote; leaving it is not.
        // 会议期间允许切入义警视图参与投票，但不允许离开。
        if (meetingActive && current == TabletChannel.POLICE) {
            return SwitchResult.MEETING_LOCKED;
        }
        if (cooldownTicksRemaining > 0) {
            return SwitchResult.COOLDOWN;
        }
        return SwitchResult.OK;
    }

    public enum SwitchResult {
        OK,
        UNCHANGED,
        DENIED,
        COOLDOWN,
        MEETING_LOCKED
    }
}
