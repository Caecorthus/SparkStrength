package annina.sparkstrength.client.role.morphling;

import annina.sparkstrength.client.render.ClientFrame;
import annina.sparkstrength.component.morphling.MorphMarkPlayerComponent;
import annina.sparkstrength.role.coroner.CoronerService;
import annina.sparkstrength.role.morphling.MorphlingCorpseRules;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.index.WatheEntities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client only. Draws a corpse-mode Morphling ({@link MorphlingCorpseRules}) as a real Wathe corpse: the entity
 * dispatcher is handed a client-side {@link PlayerBodyEntity} stand-in at the player's position
 * ({@code mixin/morphling/MorphlingCorpseStandInMixin}), so Wathe's own corpse renderer draws it: its static body model
 * (which EMF/Fresh Animations leave alone), decay, depressed-viewer fade, no shadow, no feature layers. The
 * stand-in never joins the world and is never sent anywhere; the server-side player, its hitbox and its collision are
 * untouched. Outside a world render (inventory preview) the player draws as before.
 * 仅客户端。把尸体模式的变形者（见 {@link MorphlingCorpseRules}）画成真正的 Wathe 尸体：实体分发器拿到的是位于玩家位置、
 * 仅存在于客户端的 {@link PlayerBodyEntity} 替身（{@code mixin/morphling/MorphlingCorpseStandInMixin}），由 Wathe 自己的尸体
 * 渲染器绘制：静态的尸体模型（EMF/Fresh Animations 不会改动）、腐烂、低理智观察者的淡出、没有阴影、没有附加层。替身从不
 * 加入世界，也不会发送到任何地方；服务端玩家、碰撞箱与碰撞都不受影响。世界渲染之外（背包预览）玩家照旧绘制。
 *
 * <p>Render thread only. A stand-in is made on the first draw that sees the corpse flag (a client tick could be up to a
 * tick late, leaving NoellesRoles' flat player model on screen meanwhile), freezing the facing the player has then. The
 * client tick only drops the stand-ins of players who stood up or left, so lying down again freezes a new facing.
 * 仅在渲染线程使用。替身在第一次看到尸体标记的绘制中创建（等客户端 tick 最多会晚一个 tick，期间屏幕上是 NoellesRoles 的
 * 平躺玩家模型），并固定玩家此时的朝向。客户端 tick 只负责丢掉已起身或离开的玩家的替身，因此再次躺下会固定新的朝向。</p>
 */
public final class MorphlingCorpseStandIn {
    private static final Map<UUID, Lying> LYING = new HashMap<>();
    private static @Nullable ClientWorld trackedWorld;
    private static long ticks;

    private MorphlingCorpseStandIn() {
    }

    /** End of client tick. / 客户端 tick 结束时。 */
    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != trackedWorld) {
            forget();
            trackedWorld = world;
        }
        if (world == null) {
            return;
        }
        ticks++;
        if (!LYING.isEmpty()) {
            LYING.keySet().removeIf(id -> {
                PlayerEntity player = world.getPlayerByUuid(id);
                return player == null || !drawnAsCorpse(player);
            });
        }
    }

    /** World change and disconnect: drop every stand-in. / 切换世界与断开连接时：丢掉所有替身。 */
    public static void forget() {
        LYING.clear();
        trackedWorld = null;
    }

    /**
     * The stand-in to dispatch instead of {@code entity}, posed for this frame, or null to draw {@code entity} itself.
     * 代替 {@code entity} 分发的替身（已按本帧摆好），或 null 表示照常绘制 {@code entity}。
     */
    public static @Nullable Entity standInFor(Entity entity) {
        // The live flag every draw: never a corpse for a player who stood up since the last tick.
        // 每次绘制都读实时标记：自上个 tick 起已起身的玩家绝不画成尸体。
        if (!(entity instanceof AbstractClientPlayerEntity player) || !drawnAsCorpse(player)
                || !ClientFrame.inWorldRender()) {
            return null;
        }
        Lying lying = LYING.get(player.getUuid());
        if (lying == null) {
            PlayerBodyEntity standIn = WatheEntities.PLAYER_BODY.create(player.getWorld());
            if (standIn == null) {
                return null;
            }
            lying = new Lying(standIn, player.bodyYaw, ticks - MorphlingCorpseRules.START_AGE);
            LYING.put(player.getUuid(), lying);
        }
        PlayerBodyEntity body = lying.standIn;
        body.setPlayerUuid(appearanceUuid(player));
        body.age = (int) Math.min(Integer.MAX_VALUE, ticks - lying.since);
        body.setInvisible(player.isInvisible());
        body.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), lying.yaw, 0.0F);
        body.prevBodyYaw = body.bodyYaw = lying.yaw;
        body.prevHeadYaw = body.headYaw = lying.yaw;
        return body;
    }

    private static boolean drawnAsCorpse(PlayerEntity player) {
        MorphlingPlayerComponent morphling = MorphlingPlayerComponent.KEY.getNullable(player);
        return morphling != null && MorphlingCorpseRules.drawnAsCorpse(morphling.corpseMode, player.isSpectator());
    }

    private static UUID appearanceUuid(AbstractClientPlayerEntity player) {
        MorphlingPlayerComponent morphling = MorphlingPlayerComponent.KEY.get(player);
        MorphMarkPlayerComponent mark = MorphMarkPlayerComponent.KEY.get(player);
        return MorphlingCorpseRules.appearanceUuid(
                player.getUuid(),
                morphling.getMorphTicks() > 0,
                morphling.disguise,
                mark.isActive(),
                mark.sampleUuid(),
                CoronerService.activeDisguiseUuid(player)
        );
    }

    private record Lying(PlayerBodyEntity standIn, float yaw, long since) {
    }
}
