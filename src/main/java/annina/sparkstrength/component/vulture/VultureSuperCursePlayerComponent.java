package annina.sparkstrength.component.vulture;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.vulture.VultureSuperCurseRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * The Vulture's Super Curse cooldown. The server owns it and counts it down; only the cooldown is synced, and only to
 * the Vulture (the client reads {@link #getCooldownTicks()} / {@link #isReady()} for the HUD and never decides use).
 * 秃鹫超级骂的冷却。由服务端持有并倒计时；只同步冷却值且只同步给秃鹫本人（客户端仅用于 HUD 显示，不决定能否使用）。
 *
 * <p>{@code armed} is server-only: it marks that the current Vulture stint already got its 60 s opening cooldown, so a
 * Vulture made mid-round without a usable RoleAssigned (Wraith promotion, SparkWitch conversions) is armed by the tick.
 * {@code armed} 仅存在于服务端：表示本次成为秃鹫已经领取过 60 秒开局冷却；局中未触发可依赖 RoleAssigned 而成为的秃鹫
 * （冤魂晋升、SparkWitch 转化）会由服务端 tick 补上。</p>
 */
public final class VultureSuperCursePlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<VultureSuperCursePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("vulture_super_curse"),
            VultureSuperCursePlayerComponent.class
    );

    private final PlayerEntity player;
    private int cooldownTicks;
    private boolean armed;

    public VultureSuperCursePlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    /** Remaining cooldown in ticks; synced to the owner, so the client HUD may read it. / 剩余冷却刻数，已同步给本人，客户端 HUD 可读。 */
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean isReady() {
        return cooldownTicks <= 0;
    }

    /** Becoming the Vulture: 60 s until the first curse. / 成为秃鹫：60 秒后才能首次超级骂。 */
    public void initialize() {
        armed = true;
        setCooldown(VultureSuperCurseRules.INITIAL_COOLDOWN_TICKS);
    }

    /** A curse was accepted: 90 s from now. / 超级骂已被接受：从现在起冷却 90 秒。 */
    public void startCooldown() {
        armed = true;
        setCooldown(VultureSuperCurseRules.COOLDOWN_TICKS);
    }

    /**
     * Timekeeper ability refresh: only the countdown, keeping {@code armed} so the tick does not re-grant the opening 60 s.
     * 计时者技能刷新：只清倒计时，保留 {@code armed}，避免 tick 重新发放开局 60 秒冷却。
     */
    public void clearCooldown() {
        setCooldown(0);
    }

    /** Leaves the role or the round: forget everything. / 离开该职业或对局：清空全部状态。 */
    public void clear() {
        armed = false;
        setCooldown(0);
    }

    public void sync() {
        KEY.sync(player);
    }

    private void setCooldown(int ticks) {
        int next = Math.max(0, ticks);
        if (next == cooldownTicks) {
            return;
        }
        cooldownTicks = next;
        sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // The cooldown only drives the Vulture's own HUD; nobody else needs to know the skill exists.
        // 冷却只用于秃鹫自己的 HUD，其他玩家无需得知该技能状态。
        return recipient == player;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!VultureSuperCurseRules.isVulture(GameWorldComponent.KEY.get(serverPlayer.getServerWorld()).getRole(serverPlayer))) {
            clear();
            return;
        }
        if (!armed) {
            initialize();
            return;
        }
        if (cooldownTicks > 0) {
            cooldownTicks = VultureSuperCurseRules.tickCooldown(cooldownTicks);
            if (VultureSuperCurseRules.shouldSyncCooldown(cooldownTicks)) {
                sync();
            }
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(cooldownTicks);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownTicks = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Saved so a Vulture who rejoins mid-round keeps the remaining cooldown instead of a fresh 60 s.
        // 存档保存，使局中重进的秃鹫保留剩余冷却，而不是重新领取 60 秒。
        if (cooldownTicks > 0) {
            tag.putInt("CurseCooldownTicks", cooldownTicks);
        }
        if (armed) {
            tag.putBoolean("CurseArmed", true);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        cooldownTicks = tag.contains("CurseCooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CurseCooldownTicks"))
                : 0;
        armed = tag.getBoolean("CurseArmed");
    }
}
