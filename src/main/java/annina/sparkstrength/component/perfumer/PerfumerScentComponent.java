package annina.sparkstrength.component.perfumer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.perfumer.AromaService;
import annina.sparkstrength.role.perfumer.PerfumerRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Per-player state for the Perfumer kit: Cooling Oil / Aroma debuff timers on victims and the Zephyr buff
 * on the buyer. The server owns every timer; the owner's client receives a copy (owner-only sync) and only
 * counts down locally to drive blur, overlays, instinct blocking, aim disruption and mood-drain prediction.
 * 调香师道具的玩家状态：受害者身上的风油精/香薰计时，以及购买者的晨风香水增益。所有计时由服务端持有；
 * 只同步给本人，客户端仅本地倒数，用来驱动模糊、覆盖层、本能屏蔽、准心干扰和理智下降预测。
 */
public final class PerfumerScentComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<PerfumerScentComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("perfumer_scent"),
            PerfumerScentComponent.class
    );
    private static final int AROMA_PARTICLE_INTERVAL_TICKS = 5;

    private final PlayerEntity player;
    private int coolingOilTicks;
    private int aromaTicks;
    private long aromaSeed;
    private boolean zephyr;
    private int zephyrRefreshTicks;
    /**
     * Client only, never synced or saved: ticks since the current uninterrupted Cooling Oil spell began, so a
     * refresh keeps the blur and squint going instead of ramping in from zero again.
     * 仅客户端、不同步也不保存：本次连续风油精效果开始后的 tick 数；刷新时模糊与眯眼保持延续，不会从零重新渐入。
     */
    private int coolingOilActiveTicks;

    public PerfumerScentComponent(PlayerEntity player) {
        this.player = player;
    }

    /** Server: start or refresh the Cooling Oil debuff (refresh, never stack). / 服务端：开始或刷新风油精效果（只刷新不叠加）。 */
    public void applyCoolingOil() {
        coolingOilTicks = PerfumerRules.COOLING_OIL_DURATION_TICKS;
        sync();
    }

    /** Server: start or refresh the Aroma debuff with a fresh aim-curve seed. / 服务端：开始或刷新香薰效果并更换准心曲线种子。 */
    public void applyAroma(long seed) {
        aromaTicks = PerfumerRules.AROMA_DURATION_TICKS;
        aromaSeed = seed;
        sync();
    }

    /**
     * Server: activate Zephyr for the rest of the round. Returns false when it was already active.
     * 服务端：在本局剩余时间内启用晨风香水；已启用时返回 false。
     */
    public boolean activateZephyr() {
        if (zephyr) {
            return false;
        }
        zephyr = true;
        zephyrRefreshTicks = 0;
        ensureZephyrSpeed();
        sync();
        return true;
    }

    public boolean hasCoolingOil() {
        return coolingOilTicks > 0;
    }

    public int coolingOilTicks() {
        return coolingOilTicks;
    }

    public int coolingOilActiveTicks() {
        return coolingOilActiveTicks;
    }

    public boolean hasAroma() {
        return aromaTicks > 0;
    }

    public int aromaTicks() {
        return aromaTicks;
    }

    public long aromaSeed() {
        return aromaSeed;
    }

    public boolean isZephyrActive() {
        return zephyr;
    }

    /**
     * Clears every effect. {@link #serverTick} only calls this once the player has left the living round (dead,
     * spectating, creative, round over); then Speed is removed entirely, because vanilla may keep Zephyr hidden
     * under a stronger temporary Speed and restore it later. Such players need no Speed, and Wathe clears all
     * effects at round boundaries anyway. A direct call on a still-living player removes only Zephyr's own
     * infinite level-II instance.
     * 清除全部效果。serverTick 只在玩家离开局内存活状态（死亡、旁观、创造、回合结束）后调用；此时整体移除速度，
     * 因为原版可能把晨风藏在更强的临时速度下面并在之后恢复。这些玩家不需要速度，且 Wathe 在回合边界也会清除所有效果。
     * 若对仍存活的玩家直接调用，则只移除晨风自己的无限 II 级速度。
     */
    public void reset() {
        boolean changed = coolingOilTicks > 0 || aromaTicks > 0 || zephyr;
        if (zephyr) {
            if (!GameFunctions.isPlayerPlayingAndAlive(player) || GameFunctions.isPlayerSpectatingOrCreative(player)) {
                player.removeStatusEffect(StatusEffects.SPEED);
            } else {
                removeZephyrSpeed();
            }
        }
        coolingOilTicks = 0;
        aromaTicks = 0;
        aromaSeed = 0L;
        zephyr = false;
        zephyrRefreshTicks = 0;
        if (changed) {
            sync();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Only the owner renders these effects; nobody else learns who is blinded or perfumed.
        // 只有本人需要渲染这些效果；不向其他人泄露谁被糊眼或喷了香水。
        return recipient == this.player;
    }

    @Override
    public void serverTick() {
        if (coolingOilTicks <= 0 && aromaTicks <= 0 && !zephyr) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity)
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || GameFunctions.isPlayerSpectatingOrCreative(player)) {
            // Death, round end and lobby reset all end the kit's effects. / 死亡、回合结束与大厅重置都会结束效果。
            reset();
            return;
        }

        boolean changed = false;
        if (coolingOilTicks > 0) {
            coolingOilTicks--;
            changed |= coolingOilTicks == 0;
        }
        if (aromaTicks > 0) {
            aromaTicks--;
            changed |= aromaTicks == 0;
            if (aromaTicks > 0 && aromaTicks % AROMA_PARTICLE_INTERVAL_TICKS == 0) {
                // Server-spawned so every nearby player sees who is perfumed. / 由服务端生成，附近所有人都能看到。
                AromaService.spawnHeadParticles((ServerPlayerEntity) player);
            }
        }
        if (zephyr && ++zephyrRefreshTicks >= PerfumerRules.ZEPHYR_REFRESH_INTERVAL_TICKS) {
            zephyrRefreshTicks = 0;
            ensureZephyrSpeed();
        }
        if (changed) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (coolingOilTicks > 0) {
            coolingOilTicks--;
            coolingOilActiveTicks++;
        } else {
            coolingOilActiveTicks = 0;
        }
        if (aromaTicks > 0) {
            aromaTicks--;
        }
    }

    private void ensureZephyrSpeed() {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (current != null && current.getAmplifier() >= PerfumerRules.ZEPHYR_SPEED_AMPLIFIER
                && current.isInfinite()) {
            return;
        }
        if (current != null && current.getAmplifier() > PerfumerRules.ZEPHYR_SPEED_AMPLIFIER) {
            // Never override a stronger Speed. If Zephyr was applied first, vanilla keeps it hidden underneath;
            // otherwise the next refresh after the stronger one expires re-adds it.
            // 不覆盖更强的速度。若晨风先生效，原版会把它藏在下面；否则等更强的结束后，下一次检查会补回。
            return;
        }
        // Hidden particles keep the buff invisible to others; only the owner sees the icon.
        // 隐藏粒子，其他人看不出增益；只有本人能看到图标。
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                StatusEffectInstance.INFINITE,
                PerfumerRules.ZEPHYR_SPEED_AMPLIFIER,
                false,
                false,
                true
        ));
    }

    private void removeZephyrSpeed() {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (current != null && current.isInfinite()
                && current.getAmplifier() == PerfumerRules.ZEPHYR_SPEED_AMPLIFIER) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
    }

    private void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(coolingOilTicks);
        buf.writeVarInt(aromaTicks);
        buf.writeLong(aromaSeed);
        buf.writeBoolean(zephyr);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        coolingOilTicks = buf.readVarInt();
        aromaTicks = buf.readVarInt();
        aromaSeed = buf.readLong();
        zephyr = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("CoolingOilTicks", coolingOilTicks);
        tag.putInt("AromaTicks", aromaTicks);
        tag.putLong("AromaSeed", aromaSeed);
        tag.putBoolean("Zephyr", zephyr);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        coolingOilTicks = tag.getInt("CoolingOilTicks");
        aromaTicks = tag.getInt("AromaTicks");
        aromaSeed = tag.getLong("AromaSeed");
        zephyr = tag.getBoolean("Zephyr");
    }
}
