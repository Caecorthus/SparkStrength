package annina.sparkstrength.component.undercover;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
 * Round state of the reworked Undercover. {@code stage2} goes to the owner and to effective killers (they draw the
 * silver line-of-sight highlight from it) and to nobody else, so a civilian client cannot tell who transformed once the
 * mask is off. Everything else is server-only.
 * 重做后卧底的本局状态。stage2 只同步给本人和有效杀手（杀手据此画出视线内的银色高亮），不发给其他人，
 * 这样面具摘下后平民客户端无从得知谁转变过。其余字段只在服务端。
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

    /** Round reset; the outfit and invisibility are taken off by UndercoverService before this. / 回合重置；黑衣与隐身已由 UndercoverService 先行收回。 */
    public void reset() {
        boolean wasStage2 = stage2;
        stage2 = false;
        maskBought = false;
        outfitOn = false;
        ownsInvisibility = false;
        savedChest = ItemStack.EMPTY;
        savedLegs = ItemStack.EMPTY;
        savedFeet = ItemStack.EMPTY;
        if (wasStage2) {
            KEY.sync(player);
        }
    }

    /** Re-sent periodically so a viewer who became an effective killer mid-round learns it. / 定期重发，让局中才成为有效杀手的观察者也能知道。 */
    public void resync() {
        if (stage2) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        if (recipient == player) {
            return true;
        }
        return SparkTraitsCompat.isEffectiveKiller(
                GameWorldComponent.KEY.get(recipient.getWorld()).getRole(recipient), recipient);
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
