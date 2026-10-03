package annina.sparkstrength.role.perfumer;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.component.perfumer.PerfumerScentComponent;
import annina.sparkstrength.entity.CoolingOilEntity;
import annina.sparkstrength.item.grenade.GrenadeBlastService;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * Cooling Oil: an instant throw that splashes every non-Perfumer in a grenade-style 2.5 sphere.
 * 风油精：即时投掷，按手雷式 2.5 球形遮挡判定溅到所有非调香师玩家。
 */
public final class CoolingOilService {
    private static final DustParticleEffect MINT_MIST = new DustParticleEffect(new Vector3f(0.55F, 0.95F, 0.75F), 1.3F);
    private static final DustParticleEffect MINT_CORE = new DustParticleEffect(new Vector3f(0.80F, 1.0F, 0.90F), 1.0F);
    private static final int HIT_COLOR = 0x8CF2BF;
    private static final double MIST_LIFT = 0.9D;

    private CoolingOilService() {
    }

    public static boolean throwFrom(ServerPlayerEntity player, ItemStack stack) {
        UUID roundId = PerfumerKitService.currentRoundId(player.getServerWorld());
        if (roundId == null) {
            return false;
        }
        return PerfumerKitService.launch(player, stack, new CoolingOilEntity(player.getServerWorld(), player, roundId),
                SparkStrengthItems.COOLING_OIL_ID);
    }

    /**
     * Server: burst at the impact point. The thrower and every Perfumer are immune; the rest pass the shared
     * grenade cover judgment and, while the thrower is online, the faction API. The thrower gets no hit count.
     * 服务端：在撞击点碎裂。投掷者与所有调香师免疫；其余玩家需通过共用手雷遮挡判定，
     * 投掷者在线时还需通过阵营 API。投掷者不会得到命中人数反馈。
     */
    public static void shatter(CoolingOilEntity oil, HitResult hit) {
        if (!(oil.getWorld() instanceof ServerWorld world) || !PerfumerKitService.isCurrentRound(world, oil.roundId())) {
            return;
        }
        Vec3d center = PerfumerKitService.impactPoint(oil, hit);
        Vec3d origin = PerfumerKitService.settleToFloor(world, oil, center, PerfumerRules.COOLING_OIL_MAX_SETTLE_DROP);
        PerfumerKitService.centerOn(oil, origin);
        playBurst(world, oil, center, origin);

        ServerPlayerEntity owner = oil.getOwner() instanceof ServerPlayerEntity player ? player : null;
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        // Only living round participants; lobby players standing nearby are never splashed.
        // 只波及局内存活的参与者；站在附近的大厅玩家不会被溅到。
        List<ServerPlayerEntity> candidates = world.getPlayers(player -> GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player)
                && !oil.isThrower(player)
                && !PerfumerRules.isPerfumer(game.getRole(player)));
        for (ServerPlayerEntity victim : GrenadeBlastService.filterVictims(world, oil, candidates,
                PerfumerRules.COOLING_OIL_RADIUS)) {
            if (owner != null && !SparkFactionCompat.canAffectPlayer(owner, victim, PerfumerRules.COOLING_OIL_ACTION_ID)) {
                continue;
            }
            PerfumerScentComponent.KEY.get(victim).applyCoolingOil();
            victim.sendMessage(Text.translatable("message.sparkstrength.perfumer.cooling_oil.hit")
                    .styled(style -> style.withColor(HIT_COLOR)), true);
            NbtCompound extra = new NbtCompound();
            extra.putUuid("target", victim.getUuid());
            GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.PERFUMER_COOLING_OIL_HIT, owner, extra);
        }
    }

    private static void playBurst(ServerWorld world, CoolingOilEntity oil, Vec3d center, Vec3d origin) {
        // The mist fills the body-height volume above the settled origin; gaussian spreads keep ~90% of it inside
        // the 2.5 radius. Shards and sound stay where the glass actually broke.
        // 雾气填满落点上方一人高的空间；高斯分布使约九成雾气落在 2.5 半径内。碎片与声音留在玻璃真正碎裂处。
        double mistY = origin.y + MIST_LIFT;
        world.spawnParticles(MINT_MIST, origin.x, mistY, origin.z, 48, 1.0D, 0.6D, 1.0D, 0.0D);
        world.spawnParticles(MINT_CORE, origin.x, mistY, origin.z, 16, 0.4D, 0.3D, 0.4D, 0.0D);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, oil.getStack()),
                center.x, center.y, center.z, 10, 0.05D, 0.05D, 0.05D, 0.12D);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_SPLASH_POTION_BREAK,
                SoundCategory.PLAYERS, 1.0F, 0.9F + world.getRandom().nextFloat() * 0.2F);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.PLAYERS, 0.35F, 1.6F);
    }
}
