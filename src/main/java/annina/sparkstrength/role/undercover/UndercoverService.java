package annina.sparkstrength.role.undercover;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.component.undercover.UndercoverPlayerComponent;
import annina.sparkstrength.sabotage.SabotageRules;
import annina.sparkstrength.sabotage.SabotageType;
import annina.sparkstrength.tablet.TabletStateService;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Server side of the reworked Undercover: entering stage 2 when the mask first goes on, the gun ban, the hack reward,
 * taking the mask off, the blackout look (invisible body, black leather), Speed I with the mask on in a blackout, and
 * the kill cooldown cut when the lights go out. Server thread only.
 * 重做后卧底的服务端：第一次戴上面具时进入第二阶段、禁枪、入侵奖励、摘面具、熄灯形态（身体隐身、黑色皮衣）、
 * 熄灯时戴面具的速度 I，以及灯灭时缩短击杀冷却。仅限服务端线程。
 */
public final class UndercoverService {
    /** Marks the blackout outfit so only our own pieces are ever taken back. / 标记熄灯黑衣，只收回我们自己发的。 */
    public static final String OUTFIT_MARKER = "sparkstrength_undercover_outfit";
    public static final int OUTFIT_COLOR = 0x1B1B1F;
    /** SparkWitch Bell Ringer's echo skill id, vetoed on the Undercover. / SparkWitch 敲钟人回响的技能 id，对卧底否决。 */
    public static final Identifier BELL_ECHO_ACTION = Identifier.of("sparkwitch", "bell_echo");
    private static final int INVISIBILITY_TICKS = 60;
    private static final int SPEED_TICKS = 60;
    private static final int RESYNC_TICKS = 40;

    private UndercoverService() {
    }

    public static void tick(ServerWorld world) {
        long now = world.getTime();
        boolean blackout = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
        for (ServerPlayerEntity player : world.getPlayers()) {
            UndercoverPlayerComponent state = UndercoverPlayerComponent.KEY.get(player);
            boolean undercover = UndercoverRoles.isUndercover(player);
            if (!undercover) {
                stripBoundGear(player);
            }
            boolean livingUndercover = undercover && GameFunctions.isPlayerPlayingAndAlive(player);
            if (livingUndercover && !state.isStage2() && player.getEquippedStack(EquipmentSlot.HEAD)
                    .isOf(SparkStrengthItems.cqcmMask())) {
                enterStage2(world, player, state);
            }
            boolean stage2 = livingUndercover && state.isStage2();
            if (stage2) {
                if (SabotageWorldComponent.KEY.get(world).readyTickFor(player.getUuid(), SabotageType.LIGHTS) == now) {
                    // Its own track just armed: say so to it alone. / 自己的轨道刚刚就绪：只告诉它自己。
                    player.sendMessage(Text.translatable("message.sparkstrength.undercover.sabotage_ready")
                            .formatted(Formatting.GOLD), false);
                    player.playSoundToPlayer(SparkStrengthSounds.SABOTAGE_READY, SoundCategory.PLAYERS, 0.6F, 1.2F);
                }
                // Every tick: a gun swapped in through a slot never stays long enough to fire.
                // 每 tick 检查：通过槽位换进来的枪留不到能开火的时候。
                dropGuns(player);
                if (now % RESYNC_TICKS == 0) {
                    state.resync();
                }
            }
            maintainBlackoutLook(player, state, stage2 && blackout);
            maintainBlackoutSpeed(player, state, UndercoverRules.blackoutSpeed(stage2, blackout,
                    player.getEquippedStack(EquipmentSlot.HEAD).isOf(SparkStrengthItems.cqcmMask())));
            trackKillCooldown(player, state, stage2 && blackout, now);
            if (!state.isOutfitOn()) {
                stripStrayOutfit(player);
            }
        }
    }

    private static void enterStage2(ServerWorld world, ServerPlayerEntity player, UndercoverPlayerComponent state) {
        state.enterStage2();
        state.markMaskBought();
        grantTomahawk(player);
        dropGuns(player);
        // Its own sabotage track starts armed, as if it had just hacked a box (owner 2026-10-19).
        // 它自己的破坏轨道从待命开始，与刚入侵完一样（负责人 2026-10-19）。
        SabotageWorldComponent.KEY.get(world).armUndercover(player.getUuid(),
                world.getTime() + SabotageRules.UNLOCK_DELAY_TICKS);
        player.sendMessage(Text.translatable("message.sparkstrength.undercover.stage2")
                .formatted(Formatting.GRAY), false);
        player.playSoundToPlayer(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.PLAYERS, 0.8F, 0.7F);
        TabletStateService.syncToTabletHolders(world);
    }

    private static void grantTomahawk(ServerPlayerEntity player) {
        Item tomahawk = SparkStrengthItems.tacticalTomahawk();
        if (player.getInventory().contains(stack -> stack.isOf(tomahawk))) {
            return;
        }
        ItemStack stack = tomahawk.getDefaultStack();
        if (ShopEntry.insertStackInFreeSlot(player, stack)) {
            return;
        }
        // Hotbar full (the mask went on from another slot): the tomahawk takes the selected slot and what was there
        // moves to the main inventory. / 快捷栏已满（面具从别处戴上）：斧头占用当前选中槽，原物品移到主背包。
        PlayerInventory inventory = player.getInventory();
        ItemStack previous = inventory.getMainHandStack();
        inventory.setStack(inventory.selectedSlot, stack);
        if (!previous.isEmpty() && !inventory.insertStack(previous)) {
            player.dropItem(previous, false, true);
        }
    }

    /** Stage 2 never holds a gun: any it has falls at its feet for others to take. / 第二阶段不持枪：身上的枪掉在脚下，别人可以捡。 */
    private static void dropGuns(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isIn(WatheItemTags.GUNS)) {
                inventory.setStack(slot, ItemStack.EMPTY);
                player.dropItem(stack, false, false);
            }
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (!cursor.isEmpty() && cursor.isIn(WatheItemTags.GUNS)) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            player.dropItem(cursor, false, false);
        }
    }

    /** Stage-2 guns never enter the inventory, from the ground or anywhere else. / 第二阶段的枪不会进背包，无论来自地上还是别处。 */
    public static boolean refusesStack(ServerPlayerEntity player, ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(WatheItemTags.GUNS) && UndercoverRoles.isLivingStage2(player);
    }

    /** +100 for a completed junction box hack (only stage 1 may hack). / 完成接线箱入侵 +100（只有第一阶段能入侵）。 */
    public static void onHackCompleted(ServerPlayerEntity player) {
        if (!UndercoverRoles.isUndercover(player) || UndercoverPlayerComponent.KEY.get(player).isStage2()) {
            return;
        }
        PlayerShopComponent.KEY.get(player).addToBalance(UndercoverRules.HACK_REWARD);
        player.sendMessage(Text.translatable("message.sparkstrength.undercover.hack_reward", UndercoverRules.HACK_REWARD)
                .formatted(Formatting.GOLD), true);
    }

    /** Empty hand, Shift + right-click: the mask goes back into the hotbar; stage 2 stays. / 空手 Shift + 右键：面具回到快捷栏；第二阶段不变。 */
    public static void takeMaskOff(ServerPlayerEntity player) {
        ItemStack worn = player.getEquippedStack(EquipmentSlot.HEAD);
        if (!worn.isOf(SparkStrengthItems.cqcmMask()) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        if (!ShopEntry.insertStackInFreeSlot(player, worn.copy())) {
            player.sendMessage(Text.translatable("message.sparkstrength.undercover.mask_no_room"), true);
            return;
        }
        player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.playSoundToPlayer(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.8F, 1.2F);
    }

    /** The mask, the tomahawk and the blackout outfit never leave their owner. / 面具、战术斧与熄灯黑衣永不离开主人。 */
    public static boolean isBound(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.isOf(SparkStrengthItems.cqcmMask()) || stack.isOf(SparkStrengthItems.tacticalTomahawk())) {
            return true;
        }
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(OUTFIT_MARKER);
    }

    /**
     * Stage 2 in a global blackout: invisible body and black leather (owner 2026-10-19). Kept idempotent every tick:
     * the replaced armour is saved once, only our own pieces come off, only our own invisibility is removed.
     * 第二阶段遇上全局熄灯：身体隐身并穿上黑色皮衣（负责人 2026-10-19）。每 tick 幂等维护：
     * 被替换的护甲只保存一次，只收回我们自己的衣服，只移除我们自己给的隐身。
     */
    private static void maintainBlackoutLook(ServerPlayerEntity player, UndercoverPlayerComponent state, boolean wanted) {
        if (wanted) {
            if (!state.isOutfitOn()) {
                state.putOutfitOn(player.getEquippedStack(EquipmentSlot.CHEST),
                        player.getEquippedStack(EquipmentSlot.LEGS), player.getEquippedStack(EquipmentSlot.FEET));
            }
            equipIfMissing(player, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE);
            equipIfMissing(player, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS);
            equipIfMissing(player, EquipmentSlot.FEET, Items.LEATHER_BOOTS);
            StatusEffectInstance current = player.getStatusEffect(StatusEffects.INVISIBILITY);
            if (current != null && current.getDuration() > INVISIBILITY_TICKS) {
                // Someone else's longer invisibility (a Professor serum) took over: it is theirs to end.
                // 别人更长的隐身（教授血清）接管了：由它自己结束。
                state.setOwnsInvisibility(false);
            } else if (current == null || (state.ownsInvisibility() && current.getDuration() < INVISIBILITY_TICKS / 2)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INVISIBILITY_TICKS, 0,
                        false, false, false));
                state.setOwnsInvisibility(true);
            }
            return;
        }
        takeBlackoutLookOff(player, state);
    }

    public static void takeBlackoutLookOff(ServerPlayerEntity player, UndercoverPlayerComponent state) {
        if (state.isOutfitOn()) {
            ItemStack[] saved = state.takeOutfitOff();
            restore(player, EquipmentSlot.CHEST, saved[0]);
            restore(player, EquipmentSlot.LEGS, saved[1]);
            restore(player, EquipmentSlot.FEET, saved[2]);
        }
        if (state.ownsInvisibility()) {
            state.setOwnsInvisibility(false);
            StatusEffectInstance current = player.getStatusEffect(StatusEffects.INVISIBILITY);
            // Ours never runs longer than INVISIBILITY_TICKS; anything longer was extended by another source.
            // 我们给的隐身从不超过 INVISIBILITY_TICKS；更长的说明被其他来源延长了。
            if (current != null && current.getDuration() <= INVISIBILITY_TICKS) {
                player.removeStatusEffect(StatusEffects.INVISIBILITY);
            }
        }
        releaseBlackoutSpeed(player, state);
    }

    /**
     * Speed I while the mask is on in a blackout (owner 2026-10-20), owned like the invisibility: short and refreshed,
     * no particles (the body is invisible), and a stronger or longer Speed from anyone else is left alone.
     * 熄灯时戴着面具获得速度 I（负责人 2026-10-20），归属方式与隐身相同：短时长续期、无粒子（身体是隐身的），
     * 别人给的更强或更长的速度不去碰。
     */
    private static void maintainBlackoutSpeed(ServerPlayerEntity player, UndercoverPlayerComponent state,
                                              boolean wanted) {
        if (!wanted) {
            releaseBlackoutSpeed(player, state);
            return;
        }
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (current != null && !isOwnSpeedShape(current)) {
            // Someone else's stronger or longer Speed took over: it is theirs to end.
            // 别人更强或更长的速度接管了：由它自己结束。
            state.setOwnsSpeed(false);
        } else if (current == null || (state.ownsSpeed() && current.getDuration() < SPEED_TICKS / 2)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, SPEED_TICKS,
                    UndercoverRules.BLACKOUT_SPEED_AMPLIFIER, false, false, true));
            state.setOwnsSpeed(true);
        }
    }

    private static void releaseBlackoutSpeed(ServerPlayerEntity player, UndercoverPlayerComponent state) {
        if (!state.ownsSpeed()) {
            return;
        }
        state.setOwnsSpeed(false);
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        // Speed I is the weakest Speed, so ours never hides another one underneath.
        // 速度 I 是最弱的速度，我们的效果下面不会藏着别的速度。
        if (current != null && isOwnSpeedShape(current)) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
    }

    /** Ours is never stronger than Speed I, infinite, or longer than SPEED_TICKS. / 我们给的速度不强于速度 I、不是无限、不长于 SPEED_TICKS。 */
    private static boolean isOwnSpeedShape(StatusEffectInstance effect) {
        return effect.getAmplifier() == UndercoverRules.BLACKOUT_SPEED_AMPLIFIER && !effect.isInfinite()
                && effect.getDuration() <= SPEED_TICKS;
    }

    /**
     * Forgets a kill cooldown that stopped running (ran out, or an admin cleared it); in a blackout a longer one is cut
     * to 3 s (owner 2026-10-20). Only kills record the end tick, so a door breach cooldown is never cut.
     * 忘掉已经不在跑的击杀冷却（自然结束或被管理员清除）；熄灯时更长的击杀冷却缩到 3 秒（负责人 2026-10-20）。
     * 只有击杀会记录结束 tick，所以拆门冷却永远不会被缩短。
     */
    private static void trackKillCooldown(ServerPlayerEntity player, UndercoverPlayerComponent state, boolean blackout,
                                          long now) {
        if (state.killCooldownEndTick() == 0L) {
            return;
        }
        Item tomahawk = SparkStrengthItems.tacticalTomahawk();
        ItemCooldownManager cooldowns = player.getItemCooldownManager();
        if (!cooldowns.isCoolingDown(tomahawk)) {
            state.setKillCooldownEndTick(0L);
            return;
        }
        if (UndercoverRules.blackoutCutsKillCooldown(blackout, state.killCooldownEndTick() - now)) {
            cooldowns.set(tomahawk, UndercoverRules.TOMAHAWK_BLACKOUT_KILL_COOLDOWN_TICKS);
            state.setKillCooldownEndTick(now + UndercoverRules.TOMAHAWK_BLACKOUT_KILL_COOLDOWN_TICKS);
        }
    }

    /**
     * The mask and the tomahawk only ever belong to the real Undercover: anyone else who ends up with one (a container
     * swap, an armour-slot trick) loses it at once.
     * 面具与战术斧只属于真卧底：其他人无论通过什么途径（容器交换、盔甲槽操作）拿到，都会立刻失去。
     */
    private static void stripBoundGear(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(SparkStrengthItems.cqcmMask()) || stack.isOf(SparkStrengthItems.tacticalTomahawk())) {
                inventory.setStack(slot, ItemStack.EMPTY);
            }
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (cursor.isOf(SparkStrengthItems.cqcmMask()) || cursor.isOf(SparkStrengthItems.tacticalTomahawk())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        }
    }

    /**
     * Outfit pieces outside an active blackout look (a crash mid-blackout, a reload) are left-overs: take them off.
     * 不在熄灯形态中的黑衣（熄灯中崩溃、重载）都是残留：直接收走。
     */
    private static void stripStrayOutfit(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isOutfit(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void equipIfMissing(ServerPlayerEntity player, EquipmentSlot slot, Item piece) {
        ItemStack worn = player.getEquippedStack(slot);
        if (isOutfit(worn)) {
            return;
        }
        player.equipStack(slot, outfitPiece(player, piece));
    }

    private static void restore(ServerPlayerEntity player, EquipmentSlot slot, ItemStack saved) {
        if (isOutfit(player.getEquippedStack(slot)) || player.getEquippedStack(slot).isEmpty()) {
            player.equipStack(slot, saved);
        }
    }

    private static boolean isOutfit(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(OUTFIT_MARKER);
    }

    private static ItemStack outfitPiece(ServerPlayerEntity player, Item piece) {
        ItemStack stack = piece.getDefaultStack();
        stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(OUTFIT_COLOR, false));
        // No armour points: kills never go through damage anyway, and no armour bar appears.
        // 没有护甲值：击杀本来就不走伤害，也不会出现护甲条。
        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, new AttributeModifiersComponent(List.of(), false));
        // Curse of Binding keeps it on through SparkWitch's armour slots; the glint stays hidden.
        // 绑定诅咒让它在 SparkWitch 盔甲栏里也脱不下；附魔光效隐藏。
        RegistryEntry<Enchantment> binding = player.getRegistryManager().get(RegistryKeys.ENCHANTMENT)
                .getEntry(Enchantments.BINDING_CURSE).orElse(null);
        if (binding != null) {
            ItemEnchantmentsComponent.Builder enchantments = new ItemEnchantmentsComponent.Builder(
                    ItemEnchantmentsComponent.DEFAULT);
            enchantments.add(binding, 1);
            stack.set(DataComponentTypes.ENCHANTMENTS, enchantments.build().withShowInTooltip(false));
        }
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
        NbtCompound marker = new NbtCompound();
        marker.putBoolean(OUTFIT_MARKER, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(marker));
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("item.sparkstrength.undercover_outfit"));
        return stack;
    }

    /** Role assigned or reset: start clean, taking back anything still on. / 分配身份或重置：先收回仍在身上的东西，再从干净状态开始。 */
    public static void reset(ServerPlayerEntity player) {
        UndercoverPlayerComponent state = UndercoverPlayerComponent.KEY.get(player);
        takeBlackoutLookOff(player, state);
        state.reset();
    }

    /** Death: the outfit and our invisibility come off; stage 2 itself is round state. / 死亡：收回黑衣与隐身；第二阶段本身属于本局状态。 */
    public static void afterDeath(ServerPlayerEntity victim) {
        takeBlackoutLookOff(victim, UndercoverPlayerComponent.KEY.get(victim));
    }

    /** Nothing is granted at the start: money comes from RoleEconomyService, the mask from the shop. / 开局不发放任何东西：钱来自 RoleEconomyService，面具来自商店。 */
    public static void assignForRole(ServerPlayerEntity player) {
        reset(player);
    }
}
