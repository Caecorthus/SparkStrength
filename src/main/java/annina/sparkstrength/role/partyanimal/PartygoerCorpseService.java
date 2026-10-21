package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.partyanimal.PartygoerCorpseComponent;
import annina.sparkstrength.entity.PartygoerEntity;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.index.WatheEntities;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The corpse a killed Partygoer leaves: a Wathe player body owned by the Partygoer's own UUID, which is never a player,
 * and marked with {@link PartygoerCorpseComponent} before it spawns. Rules in {@link PartygoerCorpseRules}.
 * 被打死的派对客留下的尸体：一具 Wathe 玩家尸体，主人 UUID 是派对客自己的 UUID（永远不是玩家），并在生成前写入
 * {@link PartygoerCorpseComponent} 标记。规则见 {@link PartygoerCorpseRules}。
 */
public final class PartygoerCorpseService {
    /** Wathe poison source; replay key {@code replay.poisoned.sparkstrength.partygoer_corpse[.by]}. / Wathe 中毒来源。 */
    public static final Identifier POISON_SOURCE = SparkStrength.id("partygoer_corpse");

    private PartygoerCorpseService() {
    }

    public static @Nullable PlayerBodyEntity spawn(ServerWorld world, PartygoerEntity partygoer, Identifier deathReason) {
        PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
        if (body == null) {
            return null;
        }
        body.setPlayerUuid(partygoer.getUuid());
        body.setDeathReason(deathReason);
        body.setDeathGameTime(world.getTime());
        float yaw = partygoer.getHeadYaw();
        body.refreshPositionAndAngles(partygoer.getX(), partygoer.getY(), partygoer.getZ(), yaw, 0.0F);
        body.setYaw(yaw);
        body.setHeadYaw(yaw);
        body.setBodyYaw(yaw);
        // Before spawnEntity: ENTITY_LOAD listeners (Detective crime scenes) must already see the marker.
        // 必须在 spawnEntity 之前：ENTITY_LOAD 监听器（侦探案发现场）需要已能看到标记。
        PartygoerBrain brain = partygoer.brain();
        PartygoerCorpseComponent.KEY.get(body).mark(brain != null ? brain.ownerUuid() : null);
        world.spawnEntity(body);
        return body;
    }

    /**
     * Called from NoellesRoles' Vulture eat receiver with its one body lookup, after NoellesRoles' role, life, Taotie
     * and cooldown gates. A Partygoer corpse is eaten here instead: the Vulture gets NoellesRoles' 5 s cooldown and
     * particles, the corpse is removed, and the summoner poisons the Vulture for 40 s. The empty result sends
     * NoellesRoles down its own no-body path, so the meal never counts toward the win and gives no speed, highlight or
     * skill record. Any other body list is returned untouched.
     * 由 NoellesRoles 秃鹫进食接收器在唯一一次尸体查找时调用（已通过其职业、存活、饕餮与冷却检查）。派对客尸体改在这里吃掉：
     * 秃鹫进入 NoellesRoles 同样的 5 秒冷却并播放同样的粒子，尸体被移除，召唤者让秃鹫中毒 40 秒。返回空列表使 NoellesRoles
     * 走自己的“没有尸体”分支，因此不计入胜利，也不给速度、透视或技能记录。其他尸体列表原样返回。
     */
    public static List<PlayerBodyEntity> vultureMeal(ServerPlayerEntity vulture, List<PlayerBodyEntity> found) {
        if (!PartygoerCorpseRules.vultureMealIsPartygoer(found, PartygoerCorpseComponent::isPartygoerCorpse)) {
            return found;
        }
        PlayerBodyEntity corpse = found.getFirst();
        UUID summoner = PartygoerCorpseComponent.summonerOf(corpse);
        AbilityPlayerComponent.KEY.get(vulture).setCooldown(PartygoerCorpseRules.VULTURE_COOLDOWN_TICKS);
        ServerWorld world = vulture.getServerWorld();
        Vec3d pos = corpse.getPos();
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.5, pos.z, 30, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.5, pos.z, 10, 0.2, 0.2, 0.2, 0.01);
        corpse.discard();
        PlayerPoisonComponent.KEY.get(vulture).setPoisonTicks(PartygoerCorpseRules.VULTURE_POISON_TICKS, summoner,
                POISON_SOURCE);
        return List.of();
    }
}
