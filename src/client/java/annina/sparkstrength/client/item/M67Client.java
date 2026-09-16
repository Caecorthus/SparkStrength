package annina.sparkstrength.client.item;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.client.mixin.m67.M67KeyBindingAccessor;
import annina.sparkstrength.entity.M67GrenadeEntity;
import annina.sparkstrength.item.m67.M67Rules;
import annina.sparkstrength.network.m67.M67CancelPayload;
import annina.sparkstrength.network.m67.M67SoundPayload;
import annina.sparkstrength.mixin.minecraft.ItemCooldownManagerAccessor;
import annina.sparkstrength.mixin.minecraft.ItemCooldownEntryAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public final class M67Client {
    private static ClientPlayerEntity observedPlayer;
    private static ItemStack chargedStack;
    private static ItemStack chargedActiveStack;
    private static ItemStack releasedStack;
    private static Hand chargedHand;
    private static int chargedSlot;
    private static int chargedCount;
    private static boolean waitForUseRelease;
    private static Object observedWorld;
    private static ItemStack equippedMain;
    private static ItemStack equippedOff;
    private static int equippedSlot = -1;
    private static boolean equipmentAlive;

    private M67Client() {
    }

    public static void initialize() {
        M67SoundClient.initialize();
        EntityRendererRegistry.register(SparkStrengthEntities.m67(), FlyingItemEntityRenderer::new);
        ClientTickEvents.START_CLIENT_TICK.register(M67Client::tick);
    }

    public static int outlineColor(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(entity instanceof M67GrenadeEntity) || entity.isRemoved() || client.player == null
                || client.world == null || entity.getWorld() != client.world
                || client.player.getWorld() != entity.getWorld()) {
            return -1;
        }
        // Viewer position, not camera or hitbox; share the server's inclusive cube rule.
        // 使用观察者位置而非镜头或碰撞箱；与服务端共用包含边界的立方体规则。
        return M67Rules.warningColor(client.player.getX() - entity.getX(),
                client.player.getY() - entity.getY(), client.player.getZ() - entity.getZ());
    }

    public static boolean isCharging() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && player.isUsingItem() && player.getActiveItem().isOf(SparkStrengthItems.m67());
    }

    public static boolean blocksUse(ItemStack stack) {
        return waitForUseRelease && stack.isOf(SparkStrengthItems.m67());
    }

    /** Predict before vanilla's interactItem cooldown check; the server remains authoritative.
     * 在原版 interactItem 冷却检查前预测；服务端始终拥有最终判定权。 */
    public static void refreshEquipment() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (observedPlayer != client.player || observedWorld != client.world) {
            M67SoundClient.reset();
            observedPlayer = client.player;
            observedWorld = client.world;
            chargedStack = null;
            releasedStack = null;
            waitForUseRelease = false;
            equippedMain = equippedOff = null;
            equippedSlot = -1;
            equipmentAlive = false;
        }
        ClientPlayerEntity player = client.player;
        boolean alive = player != null && client.world != null && player.getWorld() == client.world
                && player.isAlive() && !player.isRemoved();
        ItemStack main = alive ? heldM67(player.getStackInHand(Hand.MAIN_HAND)) : null;
        ItemStack off = alive ? heldM67(player.getStackInHand(Hand.OFF_HAND)) : null;
        int slot = alive ? player.getInventory().selectedSlot : -1;
        if (equipmentAlive == alive && equippedMain == main && equippedOff == off && equippedSlot == slot) {
            return;
        }
        boolean wasEquipped = equippedMain != null || equippedOff != null;
        boolean nowEquipped = main != null || off != null;
        if (wasEquipped || nowEquipped) {
            cancel();
        }
        equippedMain = main;
        equippedOff = off;
        equippedSlot = slot;
        equipmentAlive = alive;
        if (nowEquipped) {
            // Count is deliberately absent: consuming one grenade isn't a new equip.
            // 特意不比较数量：投掷消耗一枚不属于重新装备。
            var cooldown = player.getItemCooldownManager();
            var accessor = (ItemCooldownManagerAccessor) cooldown;
            Object entry = accessor.sparkstrength$getEntries().get(SparkStrengthItems.m67());
            int remaining = entry instanceof ItemCooldownEntryAccessor value
                    ? value.sparkstrength$getEndTick() - accessor.sparkstrength$getTick() : 0;
            if (remaining < M67Rules.EQUIP_COOLDOWN_TICKS) {
                cooldown.set(SparkStrengthItems.m67(), M67Rules.EQUIP_COOLDOWN_TICKS);
            }
            M67SoundClient.playLocal(M67SoundPayload.START_EQUIP);
        }
    }

    /** Only packet application may rebind equal decoded values; input mutations still use identity.
     * 仅数据包应用可重绑定相同数据；输入操作仍按引用判定。 */
    public record EquipmentSync(ClientPlayerEntity player, Object world, int slot,
                                ItemStack main, ItemStack off, ItemStack mainValue, ItemStack offValue) {}

    public static EquipmentSync beforeEquipmentSync() {
        refreshEquipment();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        ItemStack main = client.player.getStackInHand(Hand.MAIN_HAND);
        ItemStack off = client.player.getStackInHand(Hand.OFF_HAND);
        return new EquipmentSync(client.player, client.world, client.player.getInventory().selectedSlot,
                main, off, main.copy(), off.copy());
    }

    public static void afterEquipmentSync(EquipmentSync before) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (before != null && before.player() == client.player && before.world() == client.world
                && before.slot() == client.player.getInventory().selectedSlot) {
            equippedMain = reconcileSync(equippedMain, before.main(), before.mainValue(),
                    client.player.getStackInHand(Hand.MAIN_HAND));
            equippedOff = reconcileSync(equippedOff, before.off(), before.offValue(),
                    client.player.getStackInHand(Hand.OFF_HAND));
        }
        cancelChangedCharge();
        refreshEquipment();
    }

    private static ItemStack reconcileSync(ItemStack equipped, ItemStack previous, ItemStack value, ItemStack current) {
        if (equipped == null || equipped != previous) {
            return equipped;
        }
        boolean same = ItemStack.areItemsAndComponentsEqual(value, current);
        boolean consumed = releasedStack == previous && !isCharging()
                && current.getCount() == value.getCount() - 1 && (same || current.isEmpty());
        if ((same && (value.getCount() == current.getCount() || !isCharging())) || consumed) {
            if (chargedStack == previous && value.getCount() == current.getCount()) {
                chargedStack = current;
                chargedActiveStack = MinecraftClient.getInstance().player.getActiveItem();
            }
            if (releasedStack == previous) {
                releasedStack = consumed ? null : current;
            }
            return heldM67(current);
        }
        return equipped;
    }

    public static void beforeRelease() {
        if (isCharging()) {
            releasedStack = MinecraftClient.getInstance().player.getStackInHand(
                    MinecraftClient.getInstance().player.getActiveHand());
        }
        M67SoundClient.stopLocalPull();
    }

    private static ItemStack heldM67(ItemStack stack) {
        return stack.isOf(SparkStrengthItems.m67()) ? stack : null;
    }

    public static void interruptLocalAudio() {
        M67SoundClient.stopLocal();
    }

    public static void observeUse() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isCharging()) {
            return;
        }
        if (chargedActiveStack == client.player.getActiveItem() && chargedStack != null
                && chargedHand == client.player.getActiveHand()) {
            return;
        }
        observedPlayer = client.player;
        chargedStack = client.player.getStackInHand(client.player.getActiveHand());
        chargedActiveStack = client.player.getActiveItem();
        releasedStack = null;
        chargedHand = client.player.getActiveHand();
        chargedSlot = client.player.getInventory().selectedSlot;
        chargedCount = chargedStack.getCount();
        M67SoundClient.playLocal(M67SoundPayload.START_PULL);
    }

    public static void cancel() {
        boolean charging = isCharging();
        boolean equip = M67SoundClient.hasLocalEquip();
        interruptLocalAudio();
        releasedStack = null;
        if (!charging && !equip) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        // Send before any slot/release packet; clearActiveItem never emits RELEASE_USE_ITEM.
        // 必须先于切槽/松手包发送；clearActiveItem 不会触发投掷松手包。
        if (ClientPlayNetworking.canSend(M67CancelPayload.ID)) {
            ClientPlayNetworking.send(M67CancelPayload.INSTANCE);
        }
        if (!charging) {
            return;
        }
        waitForUseRelease = true;
        observedPlayer = client.player;
        chargedStack = null;
        client.player.clearActiveItem();
        while (client.options.useKey.wasPressed()) {
            // Discard queued use presses from the cancelled M67 gesture. 清除本次取消操作的使用队列。
        }
    }

    public static boolean cancelChangedCharge() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isCharging() || chargedStack == null || observedPlayer != client.player) {
            return false;
        }
        if (client.player.getInventory().selectedSlot != chargedSlot
                || client.player.getActiveHand() != chargedHand
                || client.player.getStackInHand(chargedHand) != chargedStack
                || chargedStack.getCount() != chargedCount) {
            cancel();
            return true;
        }
        return false;
    }

    public static void keyboard(long window, int key, int scanCode, int action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle()) {
            return;
        }
        InputUtil.Key input = InputUtil.fromKeyCode(key, scanCode);
        input(client, input, action);
    }

    public static void mouse(long window, int button, int action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window == client.getWindow().getHandle()) {
            input(client, InputUtil.Type.MOUSE.createFromCode(button), action);
        }
    }

    private static void input(MinecraftClient client, InputUtil.Key input, int action) {
        if (action == GLFW.GLFW_RELEASE && matches(client.options.useKey, input)) {
            waitForUseRelease = false;
            M67SoundClient.stopLocalPull();
        }
        if (action != GLFW.GLFW_PRESS || client.currentScreen != null) {
            return;
        }
        // Wathe suppresses KeyBinding queries for drop/swap; read only the bound identity.
        // Wathe 屏蔽丢弃/换手的按键查询；这里只读取绑定身份，不恢复被禁用的操作。
        if (matches(client.options.dropKey, input) || matches(client.options.swapHandsKey, input)) {
            cancel();
        }
    }

    private static boolean matches(KeyBinding binding, InputUtil.Key input) {
        return ((M67KeyBindingAccessor) binding).sparkstrength$m67BoundKey().equals(input);
    }

    private static void tick(MinecraftClient client) {
        refreshEquipment();
        M67SoundClient.tick();
        if (!client.options.useKey.isPressed()) {
            waitForUseRelease = false;
        }
        if (!isCharging()) {
            if (chargedStack != null && client.options.useKey.isPressed()) {
                waitForUseRelease = true;
            }
            chargedStack = null;
        } else if (!cancelChangedCharge() && chargedStack == null) {
            observeUse();
        }
    }
}
