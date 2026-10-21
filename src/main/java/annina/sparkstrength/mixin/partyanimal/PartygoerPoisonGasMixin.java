package annina.sparkstrength.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerWeaponHits;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.agmas.noellesroles.entity.PoisonGasCloudEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

/**
 * NoellesRoles poison gas cloud versus Partygoers (server). The cloud scans players only, poisoning them after 5 s in the
 * gas; the poison kills, so a Partygoer touching a gas block dies at once ({@code wathe:poison}, credited to the cloud's
 * owner when online). TAIL of {@code tick} is the live server path (expiry and the client return earlier); it runs
 * after the cloud spread and its player pass.
 * NoellesRoles 毒气云对派对客（服务端）。毒气云只扫描玩家，在毒气中停留 5 秒后使其中毒；中毒会致死，因此接触毒气方块的派对客立即死亡
 * （wathe:poison，主人在线时记在主人头上）。tick 的 TAIL 是存活的服务端路径（过期与客户端更早返回），在毒气扩散与玩家判定之后执行。
 */
@Mixin(PoisonGasCloudEntity.class)
public abstract class PartygoerPoisonGasMixin {
    @Shadow(remap = false)
    @Final
    private Set<BlockPos> gasBlocks;

    @Shadow(remap = false)
    private UUID ownerUuid;

    @Inject(method = "tick", at = @At("TAIL"))
    private void sparkstrength$gasKillsPartygoers(CallbackInfo ci) {
        Entity cloud = (Entity) (Object) this;
        if (cloud.getWorld() instanceof ServerWorld world && !cloud.isRemoved()) {
            PartygoerWeaponHits.onPoisonGasTick(world, gasBlocks, ownerUuid);
        }
    }
}
