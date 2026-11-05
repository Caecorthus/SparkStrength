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
     * @param undercover   real NoellesRoles Undercover role {@code UndercoverRoleRules#isUndercover} / 真实身份为 NoellesRoles 卧底
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

    /**
     * Every holder with a round role joins the public proximity channel, plus the faction networks of
     * {@link #factionNetworks}.
     * 每名拥有局内身份的持有者都会加入公共近距离频道，再加上 factionNetworks 给出的阵营网络。
     */
    public static EnumSet<TabletChannel> allowed(Facts facts) {
        if (facts == null || !facts.hasRole()) {
            return EnumSet.noneOf(TabletChannel.class);
        }
        EnumSet<TabletChannel> channels = factionNetworks(facts);
        channels.add(TabletChannel.PUBLIC);
        return channels;
    }

    private static EnumSet<TabletChannel> factionNetworks(Facts facts) {
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
            // Conscience killers secretly side with civilians; the killer network would expose teammates. They keep the
            // public channel like everyone else.
            // 善良杀手暗中站在好人一边；杀手网络会暴露队友。公共频道与其他人一样保留。
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

    /** {@link #canSwitch(Set, TabletChannel, TabletChannel, boolean, int, TabletChannel)} with no remembered faction. */
    public static SwitchResult canSwitch(
            Set<TabletChannel> allowed,
            @Nullable TabletChannel current,
            @Nullable TabletChannel target,
            boolean meetingActive,
            int cooldownTicksRemaining
    ) {
        return canSwitch(allowed, current, target, meetingActive, cooldownTicksRemaining, null);
    }

    /**
     * @param lastFaction faction network the holder last viewed ({@code null}: use {@code current} if it is one), so a
     *                    detour through the public channel never resets or dodges the faction cooldown / 持有者最后查看的阵营网络
     *                    （null 时若当前为阵营网络则用当前），经公共频道绕行既不会重置也不能躲开阵营切换冷却
     */
    public static SwitchResult canSwitch(
            Set<TabletChannel> allowed,
            @Nullable TabletChannel current,
            @Nullable TabletChannel target,
            boolean meetingActive,
            int cooldownTicksRemaining,
            @Nullable TabletChannel lastFaction
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
        if (cooldownTicksRemaining > 0 && isFactionHop(current, target, lastFaction)) {
            return SwitchResult.COOLDOWN;
        }
        return SwitchResult.OK;
    }

    /**
     * Whether switching to {@code target} moves the holder to a different faction network than the one it last viewed:
     * only those switches start or wait out the cooldown; stepping into or out of the public channel never does.
     * 切换到 target 是否让持有者换到与上次查看的不同的阵营网络：只有这类切换会开始或受制于冷却；进出公共频道从不计入。
     */
    public static boolean isFactionHop(
            @Nullable TabletChannel current,
            @Nullable TabletChannel target,
            @Nullable TabletChannel lastFaction
    ) {
        if (target == null || !target.isFaction()) {
            return false;
        }
        TabletChannel anchor = lastFaction != null ? lastFaction
                : current != null && current.isFaction() ? current : null;
        return anchor != null && anchor != target;
    }

    /**
     * Channels a running cooldown currently blocks, for the client's switcher. / 正在进行的冷却当前阻止的频道，供客户端切换器展示。
     */
    public static EnumSet<TabletChannel> cooldownBlocked(
            @Nullable Set<TabletChannel> allowed,
            @Nullable TabletChannel current,
            @Nullable TabletChannel lastFaction,
            int cooldownTicksRemaining
    ) {
        EnumSet<TabletChannel> blocked = EnumSet.noneOf(TabletChannel.class);
        if (allowed == null || cooldownTicksRemaining <= 0) {
            return blocked;
        }
        for (TabletChannel channel : allowed) {
            if (channel != current && isFactionHop(current, channel, lastFaction)) {
                blocked.add(channel);
            }
        }
        return blocked;
    }

    public enum SwitchResult {
        OK,
        UNCHANGED,
        DENIED,
        COOLDOWN,
        MEETING_LOCKED
    }
}
