package annina.sparkstrength.sabotage;

import java.util.Set;
import java.util.UUID;

/**
 * Pure rule for whose name a sabotage log line or broadcast may show: a saboteur sees their own name and the names of
 * players they linked with on the tablet (killer channel); everyone else is "???". The line itself is never hidden:
 * the darkness and the card states are public anyway, only the identity is not.
 * 破坏日志和播报能显示谁名字的纯规则：破坏者能看到自己和平板上互认过（杀手频道）的人的名字，其余人一律显示「???」。
 * 这一行本身从不隐藏：熄灯和卡片状态本来就公开，只有身份不公开。
 *
 * <p>Links come from {@code TabletWorldComponent.identityLinks}, which only the anonymous KILLER channel forms. The
 * police channel's task reveal never applies here, so an Impostor viewing the police channel learns nothing extra.
 * 互认来自 TabletWorldComponent.identityLinks，只有匿名的杀手频道会形成；义警频道按任务揭名的规则在这里不适用。</p>
 *
 * <p>Only {@code java.*} may be used here so the standalone test compiles it with plain javac.
 * 这里只能依赖 java.*，独立测试可以直接用 javac 编译。</p>
 */
public final class SabotageActorVisibility {
    private SabotageActorVisibility() {
    }

    /** A line without an actor has nothing to hide. / 没有操作者的行无需隐藏。 */
    public static boolean revealsActor(UUID actor, UUID viewer, Set<UUID> viewerLinks) {
        return actor == null || actor.equals(viewer) || viewerLinks.contains(actor);
    }
}
