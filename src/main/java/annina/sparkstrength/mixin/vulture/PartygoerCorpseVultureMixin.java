package annina.sparkstrength.mixin.vulture;

import annina.sparkstrength.role.partyanimal.PartygoerCorpseService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.VultureEatC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}), owner rule 2026-10-21: a Vulture that eats a
 * Partygoer corpse is poisoned instead of fed. Wraps the single {@code World#getEntitiesByType} body lookup of the
 * Vulture eat receiver {@code lambda$registerPackets$2}, which runs only after NoellesRoles' role, life, Taotie and
 * cooldown gates, and hands the result to {@link PartygoerCorpseService#vultureMeal}. Same seam and shape as SparkWitch's
 * Magician decoy guard; both are additive {@code @WrapOperation}s (never {@code @Redirect}) and compose in either order,
 * since a decoy is never a Partygoer corpse. The full descriptor with {@code require = 1} makes a rebuilt NoellesRoles
 * jar fail at load instead of silently dropping the hook. Class-level {@code remap = false}; the vanilla {@code World}
 * target says {@code remap = true}. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}），主人 2026-10-21 规则：秃鹫吃掉派对客尸体会中毒而不是进食。
 * 包装秃鹫进食接收器 {@code lambda$registerPackets$2} 中唯一一次 {@code World#getEntitiesByType} 尸体查找（只在
 * NoellesRoles 的职业、存活、饕餮与冷却检查之后执行），并交给 {@link PartygoerCorpseService#vultureMeal}。与 SparkWitch
 * 魔术师诱饵守卫同一接缝、同一写法；两者都是叠加式 {@code @WrapOperation}（绝不用 {@code @Redirect}），任意顺序都能组合，
 * 因为诱饵永远不是派对客尸体。完整描述符加 {@code require = 1}，NoellesRoles jar 重编后会在加载时报错，而不是悄悄失去钩子。
 * 类级 {@code remap = false}；原版 {@code World} 目标显式声明 {@code remap = true}。仅服务端。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class PartygoerCorpseVultureMixin {
    @WrapOperation(
            method = "lambda$registerPackets$2(Lorg/agmas/noellesroles/packet/VultureEatC2SPacket;"
                    + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getEntitiesByType(Lnet/minecraft/util/TypeFilter;"
                            + "Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;",
                    remap = true
            ),
            require = 1,
            allow = 1
    )
    private static List<PlayerBodyEntity> sparkstrength$vultureIsPoisonedByPartygoerCorpse(
            World world,
            TypeFilter<Entity, PlayerBodyEntity> filter,
            Box box,
            Predicate<? super PlayerBodyEntity> predicate,
            Operation<List<PlayerBodyEntity>> original,
            VultureEatC2SPacket payload,
            ServerPlayNetworking.Context context
    ) {
        return PartygoerCorpseService.vultureMeal(context.player(), original.call(world, filter, box, predicate));
    }
}
