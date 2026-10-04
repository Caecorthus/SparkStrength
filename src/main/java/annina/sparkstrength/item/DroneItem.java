package annina.sparkstrength.item;

import annina.sparkstrength.SparkStrengthDataComponents;
import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DroneCombatService;
import annina.sparkstrength.role.bomber.drone.DroneFlight;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.role.bomber.drone.DronePilotService;
import annina.sparkstrength.role.bomber.drone.DroneRules;
import annina.sparkstrength.role.bomber.drone.DroneService;
import dev.doctor4t.wathe.util.AdventureUsable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ClickType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * Inventory form of a Bomber drone. Right-click a floor to place it; the grenade drone also binds an M67 in the
 * inventory screen (cursor M67 clicked onto the drone) and recharges while carried.
 * 炸弹客无人机的物品形态。右键地面放置；投弹无人机还可在背包界面绑定 M67（光标上的 M67 点击无人机），携带时自动充电。
 */
public class DroneItem extends Item implements AdventureUsable {
    private static final int TOOLTIP_GRAY = 0x808080;
    /** How far below the clicked face the drone may settle onto a real collision surface. / 放置时向下贴合碰撞面的最大距离。 */
    private static final double SETTLE_DEPTH = 1.0;

    private final DroneKind kind;

    public DroneItem(DroneKind kind, Settings settings) {
        super(settings);
        this.kind = kind;
    }

    public DroneKind kind() {
        return kind;
    }

    /** Battery in basis points; a fresh stack is full. / 电量（万分比）；新物品为满电。 */
    public static int charge(ItemStack stack) {
        return DroneRules.clampCharge(stack.getOrDefault(SparkStrengthDataComponents.droneCharge(), DroneRules.CHARGE_MAX));
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.set(SparkStrengthDataComponents.droneCharge(), DroneRules.clampCharge(charge));
    }

    public static boolean hasPayload(ItemStack stack) {
        return stack.getOrDefault(SparkStrengthDataComponents.dronePayload(), false);
    }

    public static void setPayload(ItemStack stack, boolean payload) {
        if (payload) {
            stack.set(SparkStrengthDataComponents.dronePayload(), true);
        } else {
            stack.remove(SparkStrengthDataComponents.dronePayload());
        }
    }

    /**
     * Place on the top face of a block. Server-authoritative: only an active real Bomber, after the opening lock, off
     * cooldown, not stunned, not piloting a drone; a bomb drone also needs no other live bomb drone of theirs.
     * 放在方块顶面。由服务器判定：仅限开局锁结束、物品未冷却、未被定身、未在驾驶无人机的在场真实炸弹客；
     * 炸弹无人机还要求其场上没有其他未引爆的炸弹无人机。
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (context.getSide() != Direction.UP || player == null) {
            return ActionResult.PASS;
        }
        if (context.getWorld().isClient()) {
            // The client cannot see roles or round state; the server answers. / 客户端无法得知身份与回合状态，由服务器判定。
            return ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !(context.getWorld() instanceof ServerWorld world)) {
            return ActionResult.PASS;
        }
        if (!DroneService.isActiveBomber(serverPlayer) || serverPlayer.getItemCooldownManager().isCoolingDown(this)
                || EngineerStunnedPlayerComponent.KEY.get(serverPlayer).isStunned()
                || SparkTraitsCompat.isKillerInteractionBlocked(serverPlayer)
                // Deploying is a role skill, gated like piloting. / 部署属于职业技能，与驾驶同样受限。
                || SparkTraitsDroneCompat.isRoleSkillBlocked(serverPlayer)
                || DronePilotService.isPiloting(serverPlayer)) {
            return ActionResult.FAIL;
        }
        int opening = DroneService.openingRemaining(world);
        if (opening > 0) {
            serverPlayer.sendMessage(Text.translatable("tip.sparkstrength.drone.opening_cooldown", (opening + 19) / 20), true);
            return ActionResult.FAIL;
        }
        if (kind == DroneKind.BOMB && !DroneService.canPlaceBombDrone(serverPlayer)) {
            serverPlayer.sendMessage(Text.translatable("tip.sparkstrength.drone.bomb_active"), true);
            return ActionResult.FAIL;
        }
        UUID roundId = DroneService.currentRoundId(world);
        ItemStack stack = context.getStack();
        EntityType<DroneEntity> type = kind == DroneKind.BOMB
                ? SparkStrengthEntities.bombDrone() : SparkStrengthEntities.grenadeDrone();
        DroneEntity drone = roundId == null ? null : type.create(world);
        if (drone == null) {
            return ActionResult.FAIL;
        }
        Vec3d hit = context.getHitPos();
        drone.refreshPositionAndAngles(hit.x, hit.y, hit.z, player.getYaw(), 0.0F);
        if (!world.isBlockSpaceEmpty(drone, drone.getBoundingBox())) {
            serverPlayer.sendMessage(Text.translatable("tip.sparkstrength.drone.no_room"), true);
            drone.discard();
            return ActionResult.FAIL;
        }
        // The clicked outline may sit above the collision surface (flowers, snow layers): settle onto it so the drone
        // rests instead of hovering. / 点击的轮廓可能高于碰撞面（花、雪层）：向下贴合，使其停放而非悬停。
        Vec3d settle = Entity.adjustMovementForCollisions(drone, new Vec3d(0.0, -SETTLE_DEPTH, 0.0),
                drone.getBoundingBox(), world, List.of());
        if (settle.y > -SETTLE_DEPTH) {
            drone.refreshPositionAndAngles(hit.x, hit.y + settle.y, hit.z, player.getYaw(), 0.0F);
        }
        drone.initPlaced(serverPlayer.getUuid(), roundId, charge(stack), hasPayload(stack));
        if (!world.spawnEntity(drone)) {
            drone.discard();
            return ActionResult.FAIL;
        }
        DroneService.track(drone);
        if (drone.isRemoved()) {
            return ActionResult.FAIL;
        }
        world.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SparkStrengthSounds.DRONE_PLACE,
                SoundCategory.PLAYERS, 0.8F, 1.0F);
        DroneCombatService.recordPlaced(serverPlayer, drone);
        stack.decrement(1);
        return ActionResult.SUCCESS;
    }

    /** Server: a carried grenade drone recharges on the shared world-time beat. / 服务器：携带的投弹无人机按世界时间节拍充电。 */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient() || kind != DroneKind.GRENADE || !DroneFlight.rechargeDue(world.getTime())) {
            return;
        }
        int charge = charge(stack);
        if (charge < DroneRules.CHARGE_MAX) {
            setCharge(stack, DroneRules.recharge(charge));
        }
    }

    /** Recharging changes the stack every second; don't replay the equip animation for it. / 充电每秒都会改动物品，不重播装备动画。 */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return charge(stack) < DroneRules.CHARGE_MAX;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return DroneFlight.itemBarStep(charge(stack));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return DroneFlight.itemBarColor(charge(stack));
    }

    /**
     * Drone in a slot, cursor clicked onto it (vanilla bundle seam; runs identically on client and server): any click
     * with an M67 binds one to an unbound grenade drone; a right-click with an empty cursor unbinds it onto the cursor.
     * 无人机在槽位中、光标点击它（原版收纳袋接口，客户端与服务器结果一致）：光标持 M67 任意点击即为未挂载的投弹无人机挂载一颗；
     * 空光标右键则把 M67 卸到光标上。
     */
    @Override
    public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, ClickType clickType, PlayerEntity player,
                             StackReference cursorStackReference) {
        if (kind != DroneKind.GRENADE || !slot.canTakePartial(player)) {
            return false;
        }
        if (otherStack.isOf(SparkStrengthItems.m67()) && !hasPayload(stack)) {
            otherStack.decrement(1);
            setPayload(stack, true);
            playBindSound(player, 1.0F);
            return true;
        }
        if (clickType == ClickType.RIGHT && otherStack.isEmpty() && hasPayload(stack)) {
            setPayload(stack, false);
            cursorStackReference.set(new ItemStack(SparkStrengthItems.m67()));
            playBindSound(player, 0.8F);
            return true;
        }
        return false;
    }

    /**
     * Drone on the cursor right-clicked onto an M67 in a slot: binds one, like a bundle picking an item up.
     * 光标上的无人机右键点击槽位中的 M67：挂载一颗，类似收纳袋收取物品。
     */
    @Override
    public boolean onStackClicked(ItemStack stack, Slot slot, ClickType clickType, PlayerEntity player) {
        if (kind != DroneKind.GRENADE || clickType != ClickType.RIGHT || hasPayload(stack)
                || !slot.getStack().isOf(SparkStrengthItems.m67())) {
            return false;
        }
        if (!slot.takeStackRange(1, 1, player).isEmpty()) {
            setPayload(stack, true);
            playBindSound(player, 1.0F);
        }
        return true;
    }

    private static void playBindSound(PlayerEntity player, float pitch) {
        // Private to the binder: only the server sends it, only to them. / 只有绑定者能听到：仅由服务器发给本人。
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.playSoundToPlayer(SparkStrengthSounds.DRONE_BIND, SoundCategory.PLAYERS, 0.8F, pitch);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int charge = charge(stack);
        tooltip.add(Text.translatable("item.sparkstrength.drone.tooltip.battery", DroneRules.percent(charge))
                .styled(style -> style.withColor(DroneFlight.itemBarColor(charge)).withItalic(false)));
        String prefix = "item.sparkstrength." + kind.id() + ".tooltip.";
        if (kind == DroneKind.GRENADE) {
            tooltip.add(gray(prefix + (hasPayload(stack) ? "payload_bound" : "payload_empty")));
        }
        tooltip.add(gray(prefix + "line1"));
        tooltip.add(gray(prefix + "line2"));
        if (kind == DroneKind.GRENADE) {
            tooltip.add(gray(prefix + "line3"));
        }
        tooltip.add(gray("item.sparkstrength.drone.tooltip.opening"));
    }

    private static Text gray(String key) {
        return Text.translatable(key).styled(style -> style.withColor(TOOLTIP_GRAY).withItalic(false));
    }
}
