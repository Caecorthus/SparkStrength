package annina.sparkstrength.role.perfumer;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.component.perfumer.PerfumerScentComponent;
import annina.sparkstrength.entity.AromaOrbEntity;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
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

import java.util.UUID;

/**
 * Aroma Orb: an instant throw that only works on a direct player hit; a miss just shatters.
 * 香薰：即时投掷，只有直接砸中玩家才生效；落空只会碎裂。
 */
public final class AromaService {
    private static final DustParticleEffect LAVENDER_BURST = new DustParticleEffect(new Vector3f(0.72F, 0.55F, 0.95F), 1.1F);
    private static final DustParticleEffect LAVENDER_WISP = new DustParticleEffect(new Vector3f(0.78F, 0.64F, 0.97F), 0.7F);
    private static final int HIT_COLOR = 0xC3A6F5;

    private AromaService() {
    }

    public static boolean throwFrom(ServerPlayerEntity player, ItemStack stack) {
        UUID roundId = PerfumerKitService.currentRoundId(player.getServerWorld());
        if (roundId == null) {
            return false;
        }
        return PerfumerKitService.launch(player, stack, new AromaOrbEntity(player.getServerWorld(), player, roundId),
                SparkStrengthItems.AROMA_ORB_ID);
    }

    /**
     * Server: a direct hit perfumes the target with a fresh aim-curve seed. The thrower alone hears a hit
     * confirmation; everyone sees the lavender burst.
     * 服务端：直接命中后以新的准心曲线种子施加香薰。只有投掷者能听到命中提示音，所有人都能看到薰衣草色粒子。
     */
    public static void onPlayerHit(AromaOrbEntity orb, ServerPlayerEntity target) {
        if (!(orb.getWorld() instanceof ServerWorld world) || !PerfumerKitService.isCurrentRound(world, orb.roundId())
                || orb.isThrower(target) || !GameFunctions.isPlayerPlayingAndAlive(target)
                || !GameFunctions.isPlayerAliveAndSurvival(target)) {
            return;
        }
        ServerPlayerEntity owner = orb.getOwner() instanceof ServerPlayerEntity player ? player : null;
        if (owner != null && !SparkFactionCompat.canAffectPlayer(owner, target, PerfumerRules.AROMA_ACTION_ID)) {
            return;
        }
        PerfumerScentComponent.KEY.get(target).applyAroma(world.getRandom().nextLong());
        target.sendMessage(Text.translatable("message.sparkstrength.perfumer.aroma.hit")
                .styled(style -> style.withColor(HIT_COLOR)), true);
        world.spawnParticles(LAVENDER_BURST, target.getX(), headTop(target), target.getZ(),
                24, 0.35D, 0.25D, 0.35D, 0.0D);
        if (owner != null) {
            // Vanilla's arrow "ding": sent to the thrower's connection only. / 原版箭矢命中提示音，只发给投掷者本人。
            owner.playSoundToPlayer(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.45F, 1.25F);
        }
        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", target.getUuid());
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.PERFUMER_AROMA_HIT, owner, extra);
    }

    /** Server: cosmetic shatter for any collision, hit or miss. / 服务端：任何碰撞（命中或落空）的碎裂表现。 */
    public static void shatter(AromaOrbEntity orb, HitResult hit) {
        if (!(orb.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d center = PerfumerKitService.impactPoint(orb, hit);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, orb.getStack()),
                center.x, center.y, center.z, 6, 0.05D, 0.05D, 0.05D, 0.1D);
        world.spawnParticles(LAVENDER_WISP, center.x, center.y, center.z, 8, 0.25D, 0.2D, 0.25D, 0.0D);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_SPLASH_POTION_BREAK,
                SoundCategory.PLAYERS, 0.7F, 1.2F + world.getRandom().nextFloat() * 0.2F);
    }

    /**
     * Server, every few ticks while perfumed: small lavender wisps above the head, visible to everyone nearby.
     * 服务端：香薰期间每隔几 tick 在头顶生成少量薰衣草色粒子，附近所有人可见。
     */
    public static void spawnHeadParticles(ServerPlayerEntity player) {
        player.getServerWorld().spawnParticles(LAVENDER_WISP, player.getX(), headTop(player) + 0.15D, player.getZ(),
                2, 0.22D, 0.08D, 0.22D, 0.0D);
    }

    private static double headTop(ServerPlayerEntity player) {
        return player.getY() + player.getHeight();
    }
}
