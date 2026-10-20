package annina.sparkstrength.component.undercover;

import annina.sparkstrength.SparkStrength;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Round state of the reworked Undercover. {@code stage2} goes to every client: no faction may see a stage-2 Undercover
 * through walls, and each viewer's client decides that from this flag (killers draw silver in sight, anyone whose
 * ability would reveal them sees silver in sight, nothing out of sight). It tells no more than the client already has:
 * Wathe syncs the whole role table to everyone, and the CQCM mask that starts stage 2 is drawn on the face. CCA sends
 * it again whenever a client starts tracking the Undercover, so nothing is re-sent by hand. Everything else is
 * server-only.
 * 重做后卧底的本局状态。stage2 同步给所有客户端：任何阵营都不能透视二阶段卧底，每个观察者的客户端都要据此裁决
 * （杀手视线内银色，原本能揭示他的人视线内银色，视线外什么都没有）。它不会多泄露什么：Wathe 本就把整张身份表同步给所有人，
 * 开启第二阶段的 CQCM 面具也画在脸上。客户端开始追踪卧底时 CCA 会再发一次，无需手动重发。其余字段只在服务端。
 */
public final class UndercoverPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<UndercoverPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("undercover_player"), UndercoverPlayerComponent.class
    );

    private final PlayerEntity player;
    private boolean stage2;
    // Server-only below. / 以下仅服务端。
    private boolean maskBought;
    private boolean outfitOn;
    private boolean ownsInvisibility;
    private boolean ownsSpeed;
    /** World tick the tomahawk's last kill cooldown ends; 0 for none. / 战术斧上次击杀冷却结束的世界 tick；0 表示没有。 */
    private long killCooldownEndTick;
    private ItemStack savedChest = ItemStack.EMPTY;
    private ItemStack savedLegs = ItemStack.EMPTY;
    private ItemStack savedFeet = ItemStack.EMPTY;

    public UndercoverPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isStage2() {
        return stage2;
    }

    /** Server only; stage 2 never ends within a round (owner 2026-10-19). / 仅服务端；第二阶段本局内不会结束。 */
    public void enterStage2() {
        if (!stage2) {
            stage2 = true;
            KEY.sync(player);
        }
    }

    public boolean isMaskBought() {
        return maskBought;
    }

    public void markMaskBought() {
        maskBought = true;
    }

    public boolean isOutfitOn() {
        return outfitOn;
    }

    public boolean ownsInvisibility() {
        return ownsInvisibility;
    }

    public void setOwnsInvisibility(boolean ownsInvisibility) {
        this.ownsInvisibility = ownsInvisibility;
    }

    public boolean ownsSpeed() {
        return ownsSpeed;
    }

    public void setOwnsSpeed(boolean ownsSpeed) {
        this.ownsSpeed = ownsSpeed;
    }

    public long killCooldownEndTick() {
        return killCooldownEndTick;
    }

    public void setKillCooldownEndTick(long killCooldownEndTick) {
        this.killCooldownEndTick = killCooldownEndTick;
    }

    /** Remember what the blackout outfit replaced, once per blackout. / 每次熄灯只记录一次被黑衣替换的装备。 */
    public void putOutfitOn(ItemStack chest, ItemStack legs, ItemStack feet) {
        outfitOn = true;
        savedChest = chest.copy();
        savedLegs = legs.copy();
        savedFeet = feet.copy();
    }

    public ItemStack[] takeOutfitOff() {
        ItemStack[] saved = {savedChest, savedLegs, savedFeet};
        outfitOn = false;
        savedChest = ItemStack.EMPTY;
        savedLegs = ItemStack.EMPTY;
        savedFeet = ItemStack.EMPTY;
        return saved;
    }

    /** Round reset; the outfit, invisibility and speed are taken off by UndercoverService before this. / 回合重置；黑衣、隐身与速度已由 UndercoverService 先行收回。 */
    public void reset() {
        boolean wasStage2 = stage2;
        stage2 = false;
        maskBought = false;
        outfitOn = false;
        ownsInvisibility = false;
        ownsSpeed = false;
        killCooldownEndTick = 0L;
        savedChest = ItemStack.EMPTY;
        savedLegs = ItemStack.EMPTY;
        savedFeet = ItemStack.EMPTY;
        if (wasStage2) {
            KEY.sync(player);
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(stage2);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        stage2 = buf.readBoolean();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime-only: reconnecting or respawning starts clean. / 仅运行态：重连或重生都从干净状态开始。
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }
}
