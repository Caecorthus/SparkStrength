package annina.sparkstrength.role.bandit;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.item.BanditAmmoItem;
import annina.sparkstrength.item.BanditAttachmentItem;
import annina.sparkstrength.item.BanditRevolverItem;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the Bandit's moddable revolver outside the shot itself: the round-start grant, seating pushed rounds
 * while the gun is held, the workbench actions, and the plain revolver a dying Bandit drops.
 * 强盗改装左轮在开火之外的服务端逻辑：开局发放、手持时让塞入的子弹逐发到位、改装界面的操作，以及强盗死亡时掉落的普通左轮。
 */
public final class BanditRevolverService {
    /** Workbench and ability-key actions carried by {@code sparkstrength:bandit_revolver_action}. / 改装界面与技能键的操作。 */
    public enum Action {
        /** arg = {@link BanditAttachment#code()}: fit that part from the inventory. / 从物品栏安装该配件。 */
        INSTALL,
        /** arg = {@link BanditAttachmentSlot} ordinal: take the part off. / 卸下该部位的配件。 */
        REMOVE,
        /** arg = {@link BanditAmmo#code()}: push one round. / 塞入一发子弹。 */
        LOAD,
        /** Push rounds from the inventory until the cylinder is full. / 从物品栏塞入子弹直到装满。 */
        LOAD_ALL,
        /** arg = cylinder index (chambered first, then pending): take that round out. / 取出该位置的子弹。 */
        UNLOAD;

        public static @Nullable Action byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
        }
    }

    private static final int RECONCILE_INTERVAL_TICKS = 20;
    /** Players whose grant was decided this round. / 本局已结算发放的玩家。 */
    private static final Set<UUID> SETTLED = new HashSet<>();
    /** Ticks the oldest pending round has been seating, per player. / 每名玩家最早一发待到位子弹已装填的刻数。 */
    private static final Map<UUID, Integer> LOAD_PROGRESS = new HashMap<>();

    private BanditRevolverService() {
    }

    public static boolean isBandit(PlayerEntity player) {
        return GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.BANDIT);
    }

    public static void clearRoundState() {
        SETTLED.clear();
        LOAD_PROGRESS.clear();
    }

    /**
     * From {@code GameEvents.ON_FINISH_INITIALIZE}, like the starter tablet: roles are final and the round is not ACTIVE
     * yet. Every Bandit gets one empty revolver under the same opening lock Wathe puts on its own revolver.
     * 与开局平板一样在 ON_FINISH_INITIALIZE 调用：身份已确定，对局尚未进入 ACTIVE。每名强盗获得一把空的改装左轮，
     * 并带有与 Wathe 左轮相同的开局冷却。
     */
    public static void grantStarterRevolvers(ServerWorld world, GameWorldComponent game) {
        clearRoundState();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.isRole(player, Noellesroles.BANDIT) && SETTLED.add(player.getUuid())) {
                grant(player);
                player.getItemCooldownManager().set(SparkStrengthItems.banditRevolver(),
                        BanditRevolverRules.OPENING_COOLDOWN_TICKS);
            }
        }
    }

    /**
     * END_WORLD_TICK. Seats pending rounds for whoever holds a revolver, and once a second while the round is ACTIVE
     * reconciles: a living Bandit not settled yet (a mid-round role change) gets a revolver if they hold none, and
     * living non-Bandits lose any revolver, part or round (the gear belongs to the role).
     * END_WORLD_TICK。为手持左轮的玩家推进子弹到位；对局 ACTIVE 期间每秒对账一次：尚未结算的存活强盗（局中转职）在身上
     * 没有左轮时补发，存活的非强盗身上的左轮、配件与子弹一律收回（这些装备属于该职业）。
     */
    public static void tick(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        boolean reconcile = world.getTime() % RECONCILE_INTERVAL_TICKS == 0
                && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (reconcile && GameFunctions.isPlayerPlayingAndAlive(player) && !player.isCreative()) {
                reconcile(player, game);
            }
            tickLoading(player);
        }
    }

    private static void reconcile(ServerPlayerEntity player, GameWorldComponent game) {
        if (game.isRole(player, Noellesroles.BANDIT)) {
            if (SETTLED.add(player.getUuid()) && findRevolverSlot(player.getInventory()) < 0) {
                grant(player);
            }
            return;
        }
        if (removeGear(player)) {
            LOAD_PROGRESS.remove(player.getUuid());
        }
    }

    private static void grant(ServerPlayerEntity player) {
        ItemStack revolver = new ItemStack(SparkStrengthItems.banditRevolver());
        if (ShopEntry.insertStackInFreeSlot(player, revolver) || player.giveItemStack(revolver)) {
            player.sendMessage(Text.translatable("message.sparkstrength.bandit.revolver_granted"), false);
        }
    }

    private static void tickLoading(ServerPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        if (!BanditRevolverItem.isRevolver(held)) {
            return;
        }
        BanditRevolverState state = BanditRevolverItem.state(held);
        if (state.pending().isEmpty() || player.isSpectator()) {
            LOAD_PROGRESS.remove(player.getUuid());
            return;
        }
        int progress = LOAD_PROGRESS.merge(player.getUuid(), 1, Integer::sum);
        if (progress < BanditRevolverRules.loadTicks(state.cylinderPart())) {
            return;
        }
        LOAD_PROGRESS.remove(player.getUuid());
        BanditRevolverItem.write(held, state.withPendingSeated());
        player.getWorld().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                WatheSounds.ITEM_REVOLVER_CLICK, SoundCategory.PLAYERS, 0.35F, 1.5F);
    }

    /** Receiver of {@code sparkstrength:bandit_revolver_action}; everything is re-validated here. / 操作包接收端，全部在此复核。 */
    public static void handleAction(ServerPlayerEntity player, int actionOrdinal, int arg) {
        Action action = Action.byOrdinal(actionOrdinal);
        if (action == null || player.isSpectator()) {
            return;
        }
        if (!player.isCreative() && !(GameFunctions.isPlayerPlayingAndAlive(player) && isBandit(player))) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int gunSlot = findRevolverSlot(inventory);
        if (gunSlot < 0) {
            return;
        }
        ItemStack gun = inventory.getStack(gunSlot);
        BanditRevolverState state = BanditRevolverItem.state(gun);
        switch (action) {
            case INSTALL -> install(player, gun, state, BanditAttachment.byCode(arg));
            case REMOVE -> remove(player, gun, state, arg);
            case LOAD -> {
                BanditAmmo ammo = BanditAmmo.byCode(arg);
                if (ammo != null && !loadOne(player, gun, state, ammo)) {
                    tip(player, state.hasRoom() ? "no_ammo" : "cylinder_full");
                }
            }
            case LOAD_ALL -> loadAll(player, gun, state);
            case UNLOAD -> unload(player, gun, state, arg);
        }
        inventory.markDirty();
    }

    private static void install(ServerPlayerEntity player, ItemStack gun, BanditRevolverState state,
                                @Nullable BanditAttachment part) {
        if (part == null || state.part(part.slot()) == part
                || !BanditRevolverItem.canFit(state, part.slot(), part)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int partSlot = findItemSlot(inventory, SparkStrengthItems.banditAttachment(part));
        if (partSlot < 0) {
            return;
        }
        BanditAttachment previous = state.part(part.slot());
        // The new part's slot takes the old part back, so a swap never needs a free slot.
        // 换下来的配件放回新配件原来的格子，因此替换永远不需要空位。
        inventory.setStack(partSlot, previous == null
                ? ItemStack.EMPTY
                : new ItemStack(SparkStrengthItems.banditAttachment(previous)));
        BanditRevolverItem.write(gun, state.withPart(part.slot(), part));
    }

    private static void remove(ServerPlayerEntity player, ItemStack gun, BanditRevolverState state, int slotOrdinal) {
        if (slotOrdinal < 0 || slotOrdinal >= BanditAttachmentSlot.values().length) {
            return;
        }
        BanditAttachmentSlot slot = BanditAttachmentSlot.values()[slotOrdinal];
        BanditAttachment part = state.part(slot);
        if (part == null) {
            return;
        }
        if (!BanditRevolverItem.canFit(state, slot, null)) {
            tip(player, "cylinder_too_full");
            return;
        }
        if (!ShopEntry.insertStackInFreeSlot(player, new ItemStack(SparkStrengthItems.banditAttachment(part)))) {
            tip(player, "hotbar_full");
            return;
        }
        BanditRevolverItem.write(gun, state.withPart(slot, null));
    }

    private static boolean loadOne(ServerPlayerEntity player, ItemStack gun, BanditRevolverState state, BanditAmmo ammo) {
        if (!state.hasRoom()) {
            return false;
        }
        int ammoSlot = findItemSlot(player.getInventory(), SparkStrengthItems.banditAmmo(ammo));
        if (ammoSlot < 0) {
            return false;
        }
        player.getInventory().getStack(ammoSlot).decrement(1);
        BanditRevolverItem.write(gun, state.withPendingAdded(ammo));
        return true;
    }

    private static void loadAll(ServerPlayerEntity player, ItemStack gun, BanditRevolverState state) {
        if (!state.hasRoom()) {
            tip(player, "cylinder_full");
            return;
        }
        PlayerInventory inventory = player.getInventory();
        BanditRevolverState next = state;
        for (int slot = 0; slot < inventory.size() && next.hasRoom(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.getItem() instanceof BanditAmmoItem ammoItem) {
                while (!stack.isEmpty() && next.hasRoom()) {
                    next = next.withPendingAdded(ammoItem.ammo());
                    stack.decrement(1);
                }
            }
        }
        if (next == state) {
            tip(player, "no_ammo");
            return;
        }
        BanditRevolverItem.write(gun, next);
    }

    private static void unload(ServerPlayerEntity player, ItemStack gun, BanditRevolverState state, int index) {
        BanditAmmo ammo = state.roundAt(index);
        if (ammo == null) {
            return;
        }
        if (!giveAmmo(player, ammo)) {
            tip(player, "hotbar_full");
            return;
        }
        BanditRevolverItem.write(gun, state.withRoundRemoved(index));
        if (index == state.rounds().size()) {
            // The round that was seating left: the next one starts over. / 正在到位的那一发被取出：下一发重新计时。
            LOAD_PROGRESS.remove(player.getUuid());
        }
    }

    /**
     * One round into the hotbar: onto a stack with room first, else a free slot. Also the shop's buy handler.
     * 向快捷栏放入一发子弹：先叠到未满的同类堆上，否则放入空位。同时也是商店的购买处理。
     */
    public static boolean giveAmmo(PlayerEntity player, BanditAmmo ammo) {
        Item item = SparkStrengthItems.banditAmmo(ammo);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(item) && stack.getCount() < stack.getMaxCount()) {
                stack.increment(1);
                return true;
            }
        }
        return ShopEntry.insertStackInFreeSlot(player, new ItemStack(item));
    }

    /**
     * {@code KillPlayer.AFTER}: a dead Bandit leaves one plain Wathe revolver where they fell (owner 2026-11-12), under
     * Wathe's usual pickup rules; the modded revolver, parts and rounds are gone.
     * {@code KillPlayer.AFTER}：强盗死亡时在原地留下一把普通 Wathe 左轮（负责人 2026-11-12），拾取沿用 Wathe 的规则；
     * 改装左轮、配件与子弹随之消失。
     */
    public static void afterKill(PlayerEntity victim) {
        if (!(victim instanceof ServerPlayerEntity player) || !isBandit(player)) {
            return;
        }
        removeGear(player);
        LOAD_PROGRESS.remove(player.getUuid());
        ItemEntity drop = new ItemEntity(player.getWorld(), player.getX(), player.getY() + 0.3D, player.getZ(),
                new ItemStack(WatheItems.REVOLVER), 0.0D, 0.2D, 0.0D);
        drop.setPickupDelay(40);
        player.getWorld().spawnEntity(drop);
    }

    public static boolean isGear(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BanditRevolverItem || item instanceof BanditAttachmentItem
                || item instanceof BanditAmmoItem;
    }

    private static boolean removeGear(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        boolean removed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isGear(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                removed = true;
            }
        }
        if (isGear(player.currentScreenHandler.getCursorStack())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            removed = true;
        }
        return removed;
    }

    /** The revolver the actions work on: the held one, else the first in the inventory; -1 when none. / 操作所针对的左轮。 */
    public static int findRevolverSlot(PlayerInventory inventory) {
        if (BanditRevolverItem.isRevolver(inventory.getMainHandStack())) {
            return inventory.selectedSlot;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (BanditRevolverItem.isRevolver(inventory.getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static int findItemSlot(PlayerInventory inventory, Item item) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static void tip(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable("tip.sparkstrength.bandit_revolver." + key), true);
    }

    /** Replay/record id of the gun. / 回放记录中的枪械 id。 */
    public static Identifier itemId() {
        return SparkStrengthItems.BANDIT_REVOLVER_ID;
    }
}
