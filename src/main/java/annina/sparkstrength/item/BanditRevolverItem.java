package annina.sparkstrength.item;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthDataComponents;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.role.bandit.BanditAmmo;
import annina.sparkstrength.role.bandit.BanditAttachment;
import annina.sparkstrength.role.bandit.BanditAttachmentSlot;
import annina.sparkstrength.role.bandit.BanditRevolverRules;
import annina.sparkstrength.role.bandit.BanditRevolverState;
import annina.sparkstrength.role.partyanimal.PartygoerHitGeometry;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Bandit's moddable revolver (rules: {@link BanditRevolverRules}). Extends Wathe's revolver for the gun pose and
 * sits in {@code wathe:guns}, but never sends {@code wathe:gunshoot}: it fires through
 * {@code sparkstrength:bandit_revolver_shoot}, which the server re-validates (role, chambered round, cooldown, range
 * for the fitted barrel, line of sight).
 * 强盗的改装左轮（规则见 {@link BanditRevolverRules}）。继承 Wathe 左轮以沿用持枪姿势并加入 {@code wathe:guns}，但从不发送
 * {@code wathe:gunshoot}：经 {@code sparkstrength:bandit_revolver_shoot} 开火，由服务端复核（身份、已上膛子弹、冷却、
 * 所装枪管的射程、视线）。
 *
 * <p>Inventory clicks (vanilla's bundle hook, so client prediction and server agree without a packet): right-click the
 * revolver with a part on the cursor to fit it (a fitted part of the same slot comes back on the cursor), with a round
 * to push one into the cylinder, with an empty cursor to open the workbench.</p>
 * <p>背包点击（使用原版收纳袋钩子，客户端预测与服务端无需数据包即可一致）：光标拿着配件右键左轮即安装（同部位原有配件回到
 * 光标），拿着子弹右键则塞入一发，空手右键打开改装界面。</p>
 */
public final class BanditRevolverItem extends RevolverItem {
    public static final Identifier SPEED_MODIFIER_ID = SparkStrength.id("bandit_revolver_speed");

    /**
     * Client half, installed by the client initializer; null on a dedicated server.
     * 客户端部分，由客户端初始化器安装；专用服务端上为 null。
     */
    public interface ClientBridge {
        /** Right-click with the revolver in the main hand. / 主手持左轮时的右键。 */
        void use(PlayerEntity user, ItemStack stack);

        /** Empty-cursor right-click on the revolver in the inventory. / 在背包中空手右键左轮。 */
        void openWorkbench();
    }

    private static @Nullable ClientBridge clientBridge;

    public BanditRevolverItem(Settings settings) {
        super(settings);
    }

    public static void installClientBridge(ClientBridge bridge) {
        clientBridge = bridge;
    }

    public static boolean isRevolver(ItemStack stack) {
        return stack.getItem() instanceof BanditRevolverItem;
    }

    public static BanditRevolverState state(ItemStack stack) {
        BanditRevolverState state = stack.get(SparkStrengthDataComponents.banditRevolver());
        return state == null ? BanditRevolverState.EMPTY : state;
    }

    /**
     * Writes the state and what derives from it: the main-hand movement speed bonus of the fitted parts.
     * 写入状态及其派生数据：所装配件提供的主手移速加成。
     */
    public static void write(ItemStack stack, BanditRevolverState state) {
        stack.set(SparkStrengthDataComponents.banditRevolver(), state);
        double bonus = BanditRevolverRules.speedBonus(state.barrelPart(), state.gripPart());
        if (bonus > 0.0D) {
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.builder()
                    .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, new EntityAttributeModifier(SPEED_MODIFIER_ID, bonus,
                            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), AttributeModifierSlot.MAINHAND)
                    .build().withShowInTooltip(false));
        } else {
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        }
    }

    /**
     * Whether {@code slot} may now hold {@code part} (null = nothing): the cylinder is never changed while it holds
     * more rounds than the new one takes.
     * {@code slot} 此刻能否换成 {@code part}（null 表示卸下）：弹巢内子弹多于新弹巢容量时不允许更换弹巢。
     */
    public static boolean canFit(BanditRevolverState state, BanditAttachmentSlot slot, @Nullable BanditAttachment part) {
        return slot != BanditAttachmentSlot.CYLINDER || state.loaded() <= BanditRevolverRules.chambers(part);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) {
            return TypedActionResult.pass(stack);
        }
        if (world.isClient && clientBridge != null) {
            clientBridge.use(user, stack);
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, ClickType clickType, PlayerEntity player,
                             StackReference cursorStackReference) {
        if (clickType != ClickType.RIGHT || !slot.canTakePartial(player)) {
            return false;
        }
        if (otherStack.isEmpty()) {
            if (player.getWorld().isClient && clientBridge != null) {
                clientBridge.openWorkbench();
            }
            return true;
        }
        BanditRevolverState state = state(stack);
        if (otherStack.getItem() instanceof BanditAttachmentItem partItem && otherStack.getCount() == 1) {
            BanditAttachment part = partItem.attachment();
            BanditAttachment previous = state.part(part.slot());
            if (previous == part || !canFit(state, part.slot(), part)) {
                return true;
            }
            write(stack, state.withPart(part.slot(), part));
            cursorStackReference.set(previous == null
                    ? ItemStack.EMPTY
                    : new ItemStack(SparkStrengthItems.banditAttachment(previous)));
            playClick(player, 1.3F);
            return true;
        }
        if (otherStack.getItem() instanceof BanditAmmoItem ammoItem) {
            if (state.hasRoom()) {
                write(stack, state.withPendingAdded(ammoItem.ammo()));
                otherStack.decrement(1);
                playClick(player, 0.9F);
            }
            return true;
        }
        return false;
    }

    private static void playClick(PlayerEntity player, float pitch) {
        if (player.getWorld().isClient) {
            player.playSound(SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.4F, pitch);
        }
    }

    /**
     * Client pick for a shot of {@code range} blocks. Starts from Wathe's 30-block {@code getGunTarget}, so the client
     * hooks other mods put on it apply (SparkWitch's active-Wraith pass-through and nearer Magician puppet). A shorter
     * barrel drops whatever that ray found beyond its reach; a longer one casts again only when that ray hit nothing at
     * all. Then the same Seeker-device and Partygoer steps as the Serial Killer pistols.
     * 射程为 {@code range} 格时的客户端选靶。以 Wathe 30 格的 {@code getGunTarget} 为起点，其他模组挂在其上的客户端钩子照常
     * 生效（SparkWitch 激活冤魂穿透与更近的魔术师皮套）。短枪管丢弃该射线在射程外找到的目标；长枪管仅在该射线什么都没打到时
     * 才以更长的射程重新投射。之后与连环杀手手枪一样补上搜寻者设备与派对客两步。
     */
    public static HitResult gunTarget(PlayerEntity user, double range) {
        Vec3d eye = user.getEyePos();
        HitResult hit = RevolverItem.getGunTarget(user);
        if (range > BanditRevolverRules.BASE_RANGE_BLOCKS) {
            if (hit == null || hit.getType() == HitResult.Type.MISS) {
                hit = ProjectileUtil.getCollision(user,
                        entity -> entity instanceof PlayerEntity player && GameFunctions.isPlayerAliveAndSurvival(player),
                        (float) range);
            }
        } else if (hit != null && hit.getType() != HitResult.Type.MISS
                && hit.getPos().squaredDistanceTo(eye) > range * range) {
            Vec3d end = eye.add(user.getRotationVec(0.0F).multiply(range));
            hit = BlockHitResult.createMissed(end, Direction.UP, BlockPos.ofFloored(end));
        }
        return PartygoerHitGeometry.preferNearer(user,
                SparkWitchCompat.preferNearerGunWorldTarget(user, hit, range), range);
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return state(stack).loaded() > 0;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        BanditRevolverState state = state(stack);
        return Math.round(13.0F * state.rounds().size() / state.capacity());
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xE0B050;
    }

    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        BanditRevolverState state = state(stack);
        for (BanditAttachmentSlot slot : BanditAttachmentSlot.values()) {
            BanditAttachment part = state.part(slot);
            Text name = part == null
                    ? Text.translatable("item.sparkstrength.bandit_revolver.part_none").formatted(Formatting.DARK_GRAY)
                    : Text.translatable("item.sparkstrength.bandit_" + part.key()).formatted(Formatting.GOLD);
            tooltip.add(Text.translatable("item.sparkstrength.bandit_part.slot." + slot.key())
                    .formatted(Formatting.GRAY).append(Text.literal(": ")).append(name));
        }
        int ps12 = 0;
        int ps12b = 0;
        for (int index = 0; index < state.loaded(); index++) {
            if (state.roundAt(index) == BanditAmmo.PS12B) {
                ps12b++;
            } else {
                ps12++;
            }
        }
        tooltip.add(Text.translatable("item.sparkstrength.bandit_revolver.rounds", state.rounds().size(),
                state.capacity(), ps12, ps12b).formatted(Formatting.GRAY));
        if (!state.pending().isEmpty()) {
            tooltip.add(Text.translatable("item.sparkstrength.bandit_revolver.pending", state.pending().size())
                    .formatted(Formatting.YELLOW));
        }
        tooltip.add(Text.translatable("item.sparkstrength.bandit_revolver.hint").formatted(Formatting.DARK_GRAY));
    }
}
