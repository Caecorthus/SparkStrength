package annina.sparkstrength.role.detective;

import annina.sparkstrength.component.morphling.MorphBodyDisguiseWorldComponent;
import annina.sparkstrength.component.morphling.MorphMarkPlayerComponent;
import annina.sparkstrength.role.coroner.CoronerService;
import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.UserCache;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side "who does the detective see" resolver for magnifier clues and filed victims.
 * 服务端解析侦探“看到的是谁”，用于放大镜线索与命案受害人。
 *
 * <p>Mirrors the client appearance precedence ({@code MorphlingAppearanceClientHelper} and the NoellesRoles name
 * renderer) from server-synced state only: Jester moment / Wathe psycho / insane viewer (anonymous) > NoellesRoles
 * morph > SparkStrength reagent > Coroner disguise > real identity. The NoellesRoles insane shuffle is gated by
 * server-synced state (world {@code insaneSeesMorphs} plus the viewer's Wathe mood below depressed) and hides every
 * name, so it is reproduced as anonymous; only its per-client skin mapping and the SparkWitch
 * Wraith/BlackRaven/Curser hallucinations stay out of scope. Callers must never forward the target's real uuid to
 * the client.
 * 仅用服务端同步状态复刻客户端外观优先级：小丑时刻/Wathe 疯魔/精神错乱的观察者（匿名）> NoellesRoles 变形 >
 * SparkStrength 试剂 > 验尸官伪装 > 真实身份。NoellesRoles 精神错乱洗牌由服务端同步状态决定（世界 insaneSeesMorphs
 * 且观察者 Wathe 情绪低于抑郁），会隐藏所有名字，因此按匿名复刻；只有其客户端本地的皮肤对应关系以及
 * SparkWitch 怨灵/黑鸦/诅咒幻觉不在复刻范围内。调用方绝不能把目标的真实 UUID 发给客户端。</p>
 */
public final class DetectiveIdentityResolver {
    private static final DisplayedIdentity ANONYMOUS = new DisplayedIdentity(null, "", true);

    private DetectiveIdentityResolver() {
    }

    /**
     * Identity a living suspect shows to {@code viewer} right now (resolved at investigation time, never cached).
     * 活体嫌疑人此刻在 viewer 眼中展示的身份（调查时解析，不缓存）。
     */
    public static DisplayedIdentity resolve(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        if (isAnonymised(viewer, target)) {
            return ANONYMOUS;
        }
        return resolveAppearance(target);
    }

    /**
     * Disguise-aware identity a player's model shows, without name obfuscation. Used for a NoellesRoles Morphling in
     * corpse mode, which is filed like a body (body victims are never anonymous).
     * 玩家模型展示的身份（考虑伪装，不做名字乱码）。用于尸体模式的 NoellesRoles 变形者：它按尸体归档，而尸体受害人从不匿名。
     */
    public static DisplayedIdentity resolveAppearance(ServerPlayerEntity target) {
        UUID self = target.getUuid();
        MorphlingPlayerComponent noellesMorph = MorphlingPlayerComponent.KEY.get(target);
        MorphMarkPlayerComponent reagent = MorphMarkPlayerComponent.KEY.get(target);
        boolean reagentActive = reagent.isActive();
        // morphTicks gates the Noelles morph: stopMorph() leaves a stale disguise uuid behind.
        // Noelles 变形必须以 morphTicks > 0 为准：stopMorph() 不会清空 disguise。
        UUID displayUuid = DetectiveCaseRules.pickDisplayedUuid(
                self,
                noellesMorph.getMorphTicks() > 0, noellesMorph.disguise,
                reagentActive, reagent.sampleUuid(),
                CoronerService.activeDisguiseUuid(target)
        );
        if (displayUuid == null || displayUuid.equals(self)) {
            return new DisplayedIdentity(self, target.getGameProfile().getName(), false);
        }
        String fallback = reagentActive && displayUuid.equals(reagent.sampleUuid()) ? reagent.sampleName() : null;
        return new DisplayedIdentity(
                displayUuid,
                nameOf(target.getServer(), target.getServerWorld(), displayUuid, fallback),
                false
        );
    }

    /**
     * Apparent identity of a corpse: the SparkStrength body disguise (Coroner / morph reagent) if one is recorded for
     * its owner, else the real owner. Clients render bodies from the same owner-keyed record.
     * 尸体的表面身份：若其主人记录了 SparkStrength 尸体伪装（验尸官/变形试剂）则取伪装，否则取真实主人；
     * 客户端渲染尸体也读取同一份按主人索引的记录。
     */
    public static DisplayedIdentity resolveBody(ServerWorld world, PlayerBodyEntity body) {
        UUID owner = body.getPlayerUuid();
        Optional<MorphBodyDisguiseWorldComponent.BodyDisguise> disguise =
                MorphBodyDisguiseWorldComponent.KEY.get(world).getDisguise(owner);
        if (disguise.isPresent()) {
            UUID disguiseUuid = disguise.get().disguiseUuid();
            return new DisplayedIdentity(
                    disguiseUuid,
                    nameOf(world.getServer(), world, disguiseUuid, disguise.get().disguiseName()),
                    false
            );
        }
        return new DisplayedIdentity(owner, nameOf(world.getServer(), world, owner, null), false);
    }

    /**
     * Name lookup: online profile, then the round's Wathe game profiles, then a non-blank fallback, then the user
     * cache, then the first 8 uuid characters. Always capped to NAME_MAX_LENGTH.
     * 名字查询顺序：在线玩家 → 本局 Wathe 玩家档案 → 非空回退名 → 用户缓存 → UUID 前 8 位；结果截断到 32 字符。
     */
    public static String nameOf(@Nullable MinecraftServer server, @Nullable ServerWorld world, UUID uuid,
                                @Nullable String fallback) {
        return DetectiveCaseRules.truncate(lookupName(server, world, uuid, fallback), DetectiveCaseRules.NAME_MAX_LENGTH);
    }

    private static String lookupName(@Nullable MinecraftServer server, @Nullable ServerWorld world, UUID uuid,
                                     @Nullable String fallback) {
        if (server != null) {
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
            if (online != null) {
                return online.getGameProfile().getName();
            }
        }
        if (world != null) {
            GameProfile profile = GameWorldComponent.KEY.get(world).getGameProfiles().get(uuid);
            if (profile != null && profile.getName() != null && !profile.getName().isBlank()) {
                return profile.getName();
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        UserCache userCache = server == null ? null : server.getUserCache();
        if (userCache != null) {
            Optional<GameProfile> cached = userCache.getByUuid(uuid);
            if (cached.isPresent() && cached.get().getName() != null && !cached.get().getName().isBlank()) {
                return cached.get().getName();
            }
        }
        return uuid.toString().substring(0, 8);
    }

    /**
     * Wathe psycho mode obfuscates the target's own name; a NoellesRoles Jester moment (any Jester in psycho mode in
     * the world) obfuscates every name for living viewers, which always includes the detective. With the NoellesRoles
     * {@code insaneSeesMorphs} setting on, a viewer whose Wathe mood is below depressed sees every name as "??!?!"
     * ({@code MorphlingRoleNameRendererMixin}).
     * Wathe 疯魔模式会让目标名字乱码；NoellesRoles 小丑时刻（世界内任一小丑处于疯魔）会让存活观察者看到的所有名字乱码，
     * 侦探作为存活者必然受影响。NoellesRoles 开启 insaneSeesMorphs 时，情绪低于抑郁的观察者看到的所有名字都是“??!?!”。
     */
    private static boolean isAnonymised(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        if (PlayerPsychoComponent.KEY.get(target).getPsychoTicks() > 0) {
            return true;
        }
        ServerWorld world = target.getServerWorld();
        if (ConfigWorldComponent.KEY.get(world).insaneSeesMorphs
                && PlayerMoodComponent.KEY.get(viewer).isLowerThanDepressed()) {
            return true;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.isRole(player, Noellesroles.JESTER) && JesterPlayerComponent.KEY.get(player).inPsychoMode) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the detective saw. Anonymous identities carry no uuid and an empty name (clients draw "???" obfuscated).
     * 侦探看到的身份；匿名身份不带 UUID、名字为空（客户端绘制乱码“???”）。
     */
    public record DisplayedIdentity(@Nullable UUID displayUuid, String displayName, boolean anonymous) {
        public DisplayedIdentity {
            if (anonymous) {
                displayUuid = null;
                displayName = "";
            }
            displayName = DetectiveCaseRules.truncate(displayName, DetectiveCaseRules.NAME_MAX_LENGTH);
        }
    }
}
