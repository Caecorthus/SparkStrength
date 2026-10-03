package annina.sparkstrength.role.perfumer;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.perfumer.PerfumerScentComponent;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector3f;

/**
 * Zephyr Perfume: sprayed on oneself for Speed II and halved mood drain for the rest of the round.
 * The spray is private: sound and mist reach only the user, so nobody else learns who is buffed.
 * 晨风香水：喷在自己身上，本局剩余时间获得速度 II 与减半的理智下降。
 * 喷洒是私密的：声音与雾气只发给使用者，其他人无法得知谁获得了增益。
 */
public final class ZephyrService {
    private static final DustParticleEffect ZEPHYR_MIST = new DustParticleEffect(new Vector3f(0.86F, 0.94F, 1.0F), 0.9F);
    private static final int USED_COLOR = 0xBFE4FF;

    private ZephyrService() {
    }

    /** Server: returns true when the stack was consumed. / 服务端：消耗物品时返回 true。 */
    public static boolean spray(ServerPlayerEntity player, ItemStack stack) {
        if (!PerfumerScentComponent.KEY.get(player).activateZephyr()) {
            player.sendMessage(Text.translatable("message.sparkstrength.perfumer.zephyr.already_active")
                    .formatted(Formatting.GRAY), true);
            return false;
        }
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        player.sendMessage(Text.translatable("message.sparkstrength.perfumer.zephyr.used")
                .styled(style -> style.withColor(USED_COLOR)), true);
        player.playSoundToPlayer(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.3F, 2.0F);
        ServerWorld world = player.getServerWorld();
        world.spawnParticles(player, ZEPHYR_MIST, false, player.getX(), player.getY() + 1.1D, player.getZ(),
                10, 0.35D, 0.45D, 0.35D, 0.0D);
        GameRecordManager.recordItemUse(player, SparkStrengthItems.ZEPHYR_PERFUME_ID, null, null);
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.PERFUMER_ZEPHYR_USED, player, null);
        return true;
    }
}
