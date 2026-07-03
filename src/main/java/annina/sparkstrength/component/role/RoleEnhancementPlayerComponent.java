package annina.sparkstrength.component.role;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.NoellesRoleEnhancementRules;
import annina.sparkstrength.role.NoellesRoleIds;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * Stores per-player runtime state for SparkStrength's NoellesRoles enhancements.
 * 保存 SparkStrength 给 NoellesRoles 角色追加的玩家运行态。
 */
public final class RoleEnhancementPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<RoleEnhancementPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("role_enhancements"),
            RoleEnhancementPlayerComponent.class
    );

    private final PlayerEntity player;
    private int criminologistCooldownTicks;
    private @Nullable UUID criminologistPendingVictimUuid;
    private @Nullable UUID criminologistTrackingTargetUuid;
    private int criminologistRevealTicks;
    private int criminologistRevealIntervalTicks;

    public RoleEnhancementPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCriminologistCooldownTicks() {
        return criminologistCooldownTicks;
    }

    public @Nullable UUID getCriminologistPendingVictimUuid() {
        return criminologistPendingVictimUuid;
    }

    public @Nullable UUID getCriminologistTrackingTargetUuid() {
        return criminologistTrackingTargetUuid;
    }

    public int getCriminologistRevealTicks() {
        return criminologistRevealTicks;
    }

    public boolean isCriminologistRevealing(UUID targetUuid) {
        return criminologistRevealTicks > 0 && targetUuid.equals(criminologistTrackingTargetUuid);
    }

    public boolean hasCriminologistTarget() {
        return criminologistTrackingTargetUuid != null;
    }

    public void initializeCriminologist() {
        criminologistCooldownTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_INITIAL_COOLDOWN_TICKS;
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = null;
        criminologistRevealTicks = 0;
        criminologistRevealIntervalTicks = 0;
        sync();
    }

    public void setCriminologistPendingVictim(@Nullable UUID victimUuid) {
        if (victimUuid == null ? criminologistPendingVictimUuid == null : victimUuid.equals(criminologistPendingVictimUuid)) {
            return;
        }
        criminologistPendingVictimUuid = victimUuid;
        sync();
    }

    public void startCriminologistTracking(UUID targetUuid) {
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = targetUuid;
        criminologistCooldownTicks = 0;
        criminologistRevealTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_TICKS;
        criminologistRevealIntervalTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_INTERVAL_TICKS;
        sync();
    }

    public void startCriminologistCooldown() {
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = null;
        criminologistRevealTicks = 0;
        criminologistRevealIntervalTicks = 0;
        criminologistCooldownTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_COOLDOWN_TICKS;
        sync();
    }

    public void clearCriminologist() {
        if (criminologistCooldownTicks == 0
                && criminologistPendingVictimUuid == null
                && criminologistTrackingTargetUuid == null
                && criminologistRevealTicks == 0
                && criminologistRevealIntervalTicks == 0) {
            return;
        }
        criminologistCooldownTicks = 0;
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = null;
        criminologistRevealTicks = 0;
        criminologistRevealIntervalTicks = 0;
        sync();
    }

    public void clearAll() {
        boolean changed = criminologistCooldownTicks != 0
                || criminologistPendingVictimUuid != null
                || criminologistTrackingTargetUuid != null
                || criminologistRevealTicks != 0
                || criminologistRevealIntervalTicks != 0;
        criminologistCooldownTicks = 0;
        criminologistPendingVictimUuid = null;
        criminologistTrackingTargetUuid = null;
        criminologistRevealTicks = 0;
        criminologistRevealIntervalTicks = 0;
        if (changed) {
            sync();
        }
    }

    public void sync() {
        if (player != null) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(serverPlayer.getServerWorld());
        Role role = gameComponent.getRole(serverPlayer);
        if (!NoellesRoleIds.isDetective(role) && hasCriminologistRuntime()) {
            clearCriminologist();
        }

        tickCriminologist(serverPlayer, gameComponent, role);
    }

    private void tickCriminologist(ServerPlayerEntity serverPlayer, GameWorldComponent gameComponent, Role role) {
        if (!NoellesRoleIds.isDetective(role)) {
            return;
        }

        boolean shouldSync = false;
        if (criminologistCooldownTicks > 0) {
            criminologistCooldownTicks--;
            shouldSync |= criminologistCooldownTicks == 0 || criminologistCooldownTicks % 20 == 0;
        }

        if (criminologistTrackingTargetUuid != null) {
            if (gameComponent.isPlayerDead(criminologistTrackingTargetUuid)
                    || !targetIsPlayingAndAlive(serverPlayer.getServerWorld(), criminologistTrackingTargetUuid)) {
                startCriminologistCooldown();
                return;
            }
            if (criminologistRevealTicks > 0) {
                criminologistRevealTicks--;
                shouldSync |= criminologistRevealTicks == 0 || criminologistRevealTicks % 20 == 0;
            } else {
                criminologistRevealIntervalTicks--;
                if (criminologistRevealIntervalTicks <= 0) {
                    criminologistRevealTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_TICKS;
                    criminologistRevealIntervalTicks = NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_INTERVAL_TICKS;
                    shouldSync = true;
                }
            }
        }

        if (shouldSync) {
            sync();
        }
    }

    private static boolean targetIsPlayingAndAlive(ServerWorld world, UUID targetUuid) {
        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetUuid);
        return target != null && GameFunctions.isPlayerPlayingAndAlive(target);
    }

    private boolean hasCriminologistRuntime() {
        return criminologistCooldownTicks > 0
                || criminologistPendingVictimUuid != null
                || criminologistTrackingTargetUuid != null
                || criminologistRevealTicks > 0
                || criminologistRevealIntervalTicks > 0;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean ownerVisible = recipient == player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
        buf.writeVarInt(ownerVisible ? criminologistCooldownTicks : 0);
        writeOptionalUuid(buf, ownerVisible ? criminologistPendingVictimUuid : null);
        writeOptionalUuid(buf, ownerVisible ? criminologistTrackingTargetUuid : null);
        buf.writeVarInt(ownerVisible ? criminologistRevealTicks : 0);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        criminologistCooldownTicks = Math.max(0, buf.readVarInt());
        criminologistPendingVictimUuid = readOptionalUuid(buf);
        criminologistTrackingTargetUuid = readOptionalUuid(buf);
        criminologistRevealTicks = Math.max(0, buf.readVarInt());
        criminologistRevealIntervalTicks = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (criminologistCooldownTicks > 0) {
            tag.putInt("CriminologistCooldownTicks", criminologistCooldownTicks);
        }
        if (criminologistPendingVictimUuid != null) {
            tag.putUuid("CriminologistPendingVictim", criminologistPendingVictimUuid);
        }
        if (criminologistTrackingTargetUuid != null) {
            tag.putUuid("CriminologistTrackingTarget", criminologistTrackingTargetUuid);
        }
        if (criminologistRevealTicks > 0) {
            tag.putInt("CriminologistRevealTicks", criminologistRevealTicks);
        }
        if (criminologistRevealIntervalTicks > 0) {
            tag.putInt("CriminologistRevealIntervalTicks", criminologistRevealIntervalTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        criminologistCooldownTicks = tag.contains("CriminologistCooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CriminologistCooldownTicks"))
                : 0;
        criminologistPendingVictimUuid = tag.containsUuid("CriminologistPendingVictim")
                ? tag.getUuid("CriminologistPendingVictim")
                : null;
        criminologistTrackingTargetUuid = tag.containsUuid("CriminologistTrackingTarget")
                ? tag.getUuid("CriminologistTrackingTarget")
                : null;
        criminologistRevealTicks = tag.contains("CriminologistRevealTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CriminologistRevealTicks"))
                : 0;
        criminologistRevealIntervalTicks = tag.contains("CriminologistRevealIntervalTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CriminologistRevealIntervalTicks"))
                : 0;
    }

    private static void writeOptionalUuid(RegistryByteBuf buf, @Nullable UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeUuid(uuid);
        }
    }

    private static @Nullable UUID readOptionalUuid(RegistryByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        return buf.readUuid();
    }
}
