package annina.sparkstrength.role.bodyguard;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.bodyguard.BodyguardGearComponent;
import annina.sparkstrength.mixin.minecraft.ItemCooldownEntryAccessor;
import annina.sparkstrength.mixin.minecraft.ItemCooldownManagerAccessor;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Server side of the Democracy Shield: opening and closing a raise, its cooldown, the 2 s switch delay, the raised
 * walk speed and death cleanup. The block itself lives in BodyguardProtectionService.
 * 民主盾牌的服务端逻辑：举盾的开始与结束、冷却、2 秒切换延迟、举盾移速与死亡清理。格挡本身在 BodyguardProtectionService。
 */
public final class BodyguardShieldService {
    /** Items that get the 2 s cooldown when switched to from the shield. / 从盾切换过去时获得 2 秒冷却的物品。 */
    public static final TagKey<Item> SWAP_COOLDOWN_ITEMS =
            TagKey.of(RegistryKeys.ITEM, SparkStrength.id("shield_swap_cooldown"));
    private static final Identifier RAISED_SPEED_ID = SparkStrength.id("democracy_shield_raised");
    private static final EntityAttributeModifier RAISED_SPEED = new EntityAttributeModifier(
            RAISED_SPEED_ID,
            BodyguardRules.RAISED_SPEED_MULTIPLIER - 1.0D,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    private BodyguardShieldService() {
    }

    /**
     * Both sides (the client predicts with synced role and its simulated stamina): only a playing Bodyguard with
     * stamina left can raise it.
     * 两端通用（客户端用同步的身份与本地模拟的体力预测）：只有对局中、仍有体力的保镖能举盾。
     */
    public static boolean canRaise(PlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || !BodyguardRules.isBodyguard(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return false;
        }
        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(player);
        return stamina.isInfiniteStamina() || stamina.getSprintingTicks() > 0.0F;
    }

    /** Server gate for a raise; true opens it. / 服务端举盾校验；返回 true 即开始举盾。 */
    public static boolean tryStartRaise(ServerPlayerEntity player) {
        if (!canRaise(player)) {
            String key = BodyguardRules.isBodyguard(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                    ? "tip.sparkstrength.democracy_shield.no_stamina"
                    : "tip.sparkstrength.democracy_shield.not_bodyguard";
            player.sendMessage(Text.translatable(key).formatted(Formatting.GRAY), true);
            return false;
        }
        BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(player);
        Item shield = SparkStrengthItems.democracyShield();
        if (gear.lastMainHandItem() != shield) {
            // Switched and right-clicked in the same tick, before the world tick saw the switch. / 同一 tick 内切换并右键，世界 tick 尚未看到切换。
            gear.setLastMainHandItem(shield);
            onSwitchedToShield(player, gear);
            return false;
        }
        long now = player.getServerWorld().getTime();
        if (gear.shieldCooldownUntil() > now) {
            reassertCooldown(player, gear, now);
            return false;
        }
        gear.startRaise(now);
        return true;
    }

    /** Closes the open raise once, with the cooldown its length earned. / 结束当前举盾（只结算一次），按举盾时长给冷却。 */
    public static void onLowered(ServerPlayerEntity player, int raisedTicks) {
        BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(player);
        if (!gear.isRaising()) {
            return;
        }
        gear.endRaise();
        removeRaisedSpeed(player);
        applyCooldown(player, gear, BodyguardRules.cooldownAfterLowering(raisedTicks));
    }

    /**
     * Stamina ran out on a block: force the shield down with the break sound and the full cooldown.
     * 格挡耗尽体力：强制放下盾，播放破盾音效并进入完整冷却。
     */
    public static void breakShield(ServerPlayerEntity player) {
        BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(player);
        gear.endRaise();
        // stopUsingItem calls onStoppedUsing, which now finds no open raise. / stopUsingItem 会调用 onStoppedUsing，此时已无进行中的举盾。
        player.stopUsingItem();
        removeRaisedSpeed(player);
        applyCooldown(player, gear, BodyguardRules.BREAK_COOLDOWN_TICKS);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0F, 0.8F + player.getRandom().nextFloat() * 0.4F);
    }

    /** Whether the shield is up long enough to block. / 盾是否已举起足够久、可以格挡。 */
    public static boolean isRaisedForBlocking(PlayerEntity player) {
        return player.isUsingItem()
                && player.getActiveItem().isOf(SparkStrengthItems.democracyShield())
                && BodyguardRules.isBlockingRaise(player.getItemUseTime());
    }

    /** END_WORLD_TICK: raise upkeep, cooldown authority and switch detection. / 世界 tick 末：举盾维护、冷却权威与切换检测。 */
    public static void tick(ServerWorld world) {
        long now = world.getTime();
        Item shield = SparkStrengthItems.democracyShield();
        for (ServerPlayerEntity player : world.getPlayers()) {
            BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(player);
            boolean raised = player.isUsingItem() && player.getActiveItem().isOf(shield);
            if (raised && gear.isRaising()) {
                applyRaisedSpeed(player);
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }
            } else {
                if (gear.isRaising()) {
                    // A hotbar switch clears the active item without onStoppedUsing. / 切换快捷栏会清除使用中物品且不调用 onStoppedUsing。
                    onLowered(player, (int) Math.min(Integer.MAX_VALUE, now - gear.raiseStartTick()));
                }
                if (raised) {
                    // A raise the server never opened (e.g. no longer a Bodyguard): drop it. / 服务端未开启的举盾（如已不是保镖）：直接放下。
                    player.stopUsingItem();
                }
                removeRaisedSpeed(player);
            }
            if (gear.shieldCooldownUntil() > now) {
                reassertCooldown(player, gear, now);
            }
            detectSwitch(player, gear, shield);
        }
    }

    /** Death or round cleanup: no raise, no speed, no vest, no shield left behind. / 死亡或回合清理：不留举盾、移速、防弹衣与盾。 */
    public static void clearGear(ServerPlayerEntity player, boolean removeGearItems) {
        BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(player);
        gear.endRaise();
        removeRaisedSpeed(player);
        if (removeGearItems) {
            Item shield = SparkStrengthItems.democracyShield();
            Item vest = SparkStrengthItems.bodyguardVest();
            if (player.getActiveItem().isOf(shield)) {
                player.clearActiveItem();
            }
            PlayerInventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                // Covers the armour slots too, so a worn vest goes as well. / 同时覆盖护甲栏，穿着的防弹衣也一并移除。
                if (inventory.getStack(slot).isOf(shield) || inventory.getStack(slot).isOf(vest)) {
                    inventory.setStack(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    public static void resetRound(ServerPlayerEntity player) {
        removeRaisedSpeed(player);
        BodyguardGearComponent.KEY.get(player).reset();
    }

    private static void detectSwitch(ServerPlayerEntity player, BodyguardGearComponent gear, Item shield) {
        ItemStack held = player.getMainHandStack();
        Item heldItem = held.getItem();
        Item last = gear.lastMainHandItem();
        if (heldItem == last) {
            return;
        }
        gear.setLastMainHandItem(heldItem);
        if (heldItem == shield) {
            onSwitchedToShield(player, gear);
        } else if (last == shield && held.isIn(SWAP_COOLDOWN_ITEMS)) {
            preserveCooldown(player, heldItem, BodyguardRules.SWITCH_DELAY_TICKS);
        }
    }

    private static void onSwitchedToShield(ServerPlayerEntity player, BodyguardGearComponent gear) {
        long now = player.getServerWorld().getTime();
        if (gear.shieldCooldownUntil() - now < BodyguardRules.SWITCH_DELAY_TICKS) {
            applyCooldown(player, gear, BodyguardRules.SWITCH_DELAY_TICKS);
        }
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Sets the shield cooldown and records its real end; Fast Hands may have shortened the vanilla entry.
     * 设置盾的冷却并记录真实结束时间；快手词条可能已缩短原版冷却条目。
     */
    private static void applyCooldown(ServerPlayerEntity player, BodyguardGearComponent gear, int ticks) {
        Item shield = SparkStrengthItems.democracyShield();
        player.getItemCooldownManager().set(shield, ticks);
        int actual = remainingCooldown(player.getItemCooldownManager(), shield);
        gear.setShieldCooldownUntil(player.getServerWorld().getTime() + Math.max(0, actual));
    }

    /** Puts back a shield cooldown that something cleared (Timekeeper refresh, rejoin). / 补回被清掉的盾冷却（计时员刷新、重新加入）。 */
    private static void reassertCooldown(ServerPlayerEntity player, BodyguardGearComponent gear, long now) {
        Item shield = SparkStrengthItems.democracyShield();
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (remainingCooldown(manager, shield) > 0) {
            return;
        }
        int left = (int) Math.min(Integer.MAX_VALUE, gear.shieldCooldownUntil() - now);
        if (left > 0) {
            manager.set(shield, left);
        }
    }

    private static void preserveCooldown(ServerPlayerEntity player, Item item, int minimumTicks) {
        ItemCooldownManager manager = player.getItemCooldownManager();
        if (remainingCooldown(manager, item) < minimumTicks) {
            manager.set(item, minimumTicks);
        }
    }

    private static int remainingCooldown(ItemCooldownManager manager, Item item) {
        Object entry = ((ItemCooldownManagerAccessor) manager).sparkstrength$getEntries().get(item);
        return entry == null ? 0 : Math.max(0, ((ItemCooldownEntryAccessor) entry).sparkstrength$getEndTick()
                - ((ItemCooldownManagerAccessor) manager).sparkstrength$getTick());
    }

    private static void applyRaisedSpeed(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(RAISED_SPEED_ID)) {
            speed.addTemporaryModifier(RAISED_SPEED);
        }
    }

    private static void removeRaisedSpeed(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(RAISED_SPEED_ID)) {
            speed.removeModifier(RAISED_SPEED_ID);
        }
    }
}
