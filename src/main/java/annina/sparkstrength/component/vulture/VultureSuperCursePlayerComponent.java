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
 * The Vulture's Super Curse cooldown. The server owns it as the world time the curse is ready again; only that time is
 * synced, and only to the Vulture (the client reads {@link #readyAtTick()} for the HUD and never decides use).
 * 秃鹫超级骂的冷却。由服务端持有，记录为超级骂再次可用的世界时间；只同步该时间且只同步给秃鹫本人（客户端仅用于 HUD
 * 显示，不决定能否使用）。
 *
 * <p>A world-time deadline, not a synced countdown, so the HUD reads it on the same clock as the skateboard's lock.
 * The countdown used to be sent once a second while the board counted on the client's own clock, so the two 60 s
 * openings could read different seconds: a second apart at best, several after the server stalled.
 * 使用世界时间的截止时刻而非同步的倒数，HUD 便可与滑板的锁按同一时钟读取。此前倒数每秒同步一次，而滑板按客户端自己的
 * 时钟倒数，两个 60 秒的开局冷却因此可能显示不同的秒数：最好情况下相差一秒，服务端卡顿后相差数秒。</p>
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
    /** World time the curse is ready again; 0 when ready. / 超级骂再次可用的世界时间；可用时为 0。 */
    private long readyAtTick;
    private boolean armed;

    public VultureSuperCursePlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    /** World time the curse is ready again (0 when ready); synced to the owner for the HUD. / 超级骂再次可用的世界时间（可用时为 0），同步给本人供 HUD 使用。 */
    public long readyAtTick() {
        return readyAtTick;
    }

    /** Remaining cooldown in ticks on this side's world clock. / 按本端世界时钟计算的剩余冷却刻数。 */
    public int getCooldownTicks() {
        return VultureSuperCurseRules.cooldownRemaining(readyAtTick, player.getWorld().getTime());
    }

    public boolean isReady() {
        return getCooldownTicks() <= 0;
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
        long next = ticks <= 0 ? 0L : player.getWorld().getTime() + ticks;
        if (next == readyAtTick) {
            return;
        }
        readyAtTick = next;
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
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarLong(readyAtTick);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        readyAtTick = Math.max(0L, buf.readVarLong());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Saved so a Vulture who rejoins mid-round keeps the remaining cooldown instead of a fresh 60 s. Stored as the
        // remainder, as before, so time offline still does not count.
        // 存档保存，使局中重进的秃鹫保留剩余冷却，而不是重新领取 60 秒。与此前一样保存剩余量，离线时间仍不计入。
        int cooldownTicks = getCooldownTicks();
        if (cooldownTicks > 0) {
            tag.putInt("CurseCooldownTicks", cooldownTicks);
        }
        if (armed) {
            tag.putBoolean("CurseArmed", true);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        int cooldownTicks = tag.contains("CurseCooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CurseCooldownTicks"))
                : 0;
        readyAtTick = cooldownTicks > 0 ? player.getWorld().getTime() + cooldownTicks : 0L;
        armed = tag.getBoolean("CurseArmed");
    }
}
