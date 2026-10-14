package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.record.AchievementRecords;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Achievement record (S14): each player a Bandit throwing axe hits, after NoellesRoles' own checks (a live survival
 * player, not the thrower) and after its kill call returned, so {@code killed} reflects shields, vests and fake deaths.
 * The throw id is the axe entity's uuid, as in the S13 throw event. Coexists with SparkWitch's Judge wrapper on the
 * same call; the kill itself is unchanged.
 * 成就记录（S14）：强盗飞斧命中的每名玩家，记录在 NoellesRoles 自身检查（存活的生存模式玩家、不是投掷者）之后、
 * 其击杀调用返回之后，因此 killed 已反映护盾、防弹衣与假死。投掷编号为飞斧实体的 UUID，与 S13 投掷事件相同。
 * 与 SparkWitch 法官在同一调用上的包装共存；击杀本身不变。
 */
@Mixin(ThrowingAxeEntity.class)
public abstract class ThrowingAxeHitRecordMixin {
    @WrapOperation(
            method = "onEntityHit",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"),
            require = 1,
            allow = 1
    )
    private void sparkstrength$recordAxeHit(ServerPlayerEntity victim, boolean spawnBody,
                                            @Nullable ServerPlayerEntity killer, Identifier deathReason,
                                            Operation<Void> original) {
        boolean deadBefore = AchievementRecords.isMarkedDead(victim);
        original.call(victim, spawnBody, killer, deathReason);
        AchievementRecords.throwingAxeHit(killer, victim, ((ThrowingAxeEntity) (Object) this).getUuid(), deadBefore);
    }
}
