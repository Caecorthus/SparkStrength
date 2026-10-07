package annina.sparkstrength.role.serialkiller;

import annina.sparkstrength.item.SerialPistolItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * Binding rules for the two serial pistols, used by the guard mixins in {@code mixin/serialkiller/} and by
 * {@link SerialPistolGuardService}. The pistols exist only while their Serial Killer is in psycho mode: they never
 * become item entities, never drop on death, never move out of the slot psycho put them in, are never handed to a
 * world container (item frame, armor stand, allay, decorated pot), and are stripped from anyone else. Identified by
 * class, so the checks stay safe before registry lookups. Mirrors, never shares, SparkWitch's bound-item guards.
 * 两把连环手枪的绑定规则，供 {@code mixin/serialkiller/} 中的防护 mixin 与 {@link SerialPistolGuardService} 使用。
 * 手枪只在其连环杀手疯魔期间存在：永不变成掉落物、死亡不掉落、不能离开疯魔放入的栏位、不能交给世界容器
 * （物品展示框、盔甲架、悦灵、饰纹陶罐），且会从其他任何人身上收走。按物品类识别，不依赖注册表。
 * 仿照（但不共享）SparkWitch 的绑定物品防护。
 */
public final class SerialPistolInventoryRules {
    private SerialPistolInventoryRules() {
    }

    public static boolean isPistol(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SerialPistolItem;
    }

    /** Refuses every drop of a pistol into an item entity (Q, Ctrl+Q, any server drop path). / 拒绝把手枪丢成掉落物。 */
    public static boolean blocksDrop(@Nullable ItemStack stack) {
        return isPistol(stack);
    }

    /** Excludes the pistols from Wathe's death-drop loop. / 将手枪排除出 Wathe 死亡掉落流程。 */
    public static boolean blocksDeathDrop(@Nullable ItemStack stack) {
        return isPistol(stack);
    }

    /**
     * Holder entitlement: playing (round running, has a role), alive, exactly the Serial Killer and in psycho mode.
     * 持有资格：正在对局（回合进行、有身份）、存活、恰好是连环杀手且处于疯魔。
     */
    public static boolean isEntitled(boolean playingAndAlive, boolean serialKiller, int psychoTicks) {
        return playingAndAlive && serialKiller && psychoTicks > 0;
    }

    /**
     * Sweep scope: creative players are left alone (they can clone any item anyway); everyone else who is not
     * entitled loses every pistol copy.
     * 清扫范围：创造模式玩家不处理（本就可复制任意物品）；其余无资格者会失去所有手枪副本。
     */
    public static boolean sweeps(boolean creative, boolean entitled) {
        return !creative && !entitled;
    }

    /**
     * World-interaction veto for the stack in the used hand: vanilla 1.21.1 item frames (glow included), armor stands
     * and allays take the held stack, and a decorated pot inserts any held item.
     * 针对所用手中物品的世界交互否决：原版 1.21.1 中物品展示框（含荧光）、盔甲架与悦灵会拿走手持物品，饰纹陶罐会放入任意手持物品。
     */
    public static boolean blocksEntityUse(@Nullable ItemStack held, @Nullable Entity target) {
        return isPistol(held) && target != null && takesHeldStack(target.getClass());
    }

    /** Block counterpart of {@link #blocksEntityUse}. / {@link #blocksEntityUse} 的方块版本。 */
    public static boolean blocksBlockUse(@Nullable ItemStack held, @Nullable BlockState target) {
        return isPistol(held) && target != null && takesHeldStack(target.getBlock().getClass());
    }

    /** Pure class check, testable without a bootstrapped registry. / 纯类判断，无需引导注册表即可测试。 */
    static boolean takesHeldStack(@Nullable Class<?> targetType) {
        return targetType != null
                && (ItemFrameEntity.class.isAssignableFrom(targetType)
                || ArmorStandEntity.class.isAssignableFrom(targetType)
                || AllayEntity.class.isAssignableFrom(targetType)
                || DecoratedPotBlock.class.isAssignableFrom(targetType));
    }

    /**
     * Server-side slot-click veto. Gathers side-effect-free facts from the live handler and decides through the pure
     * {@link #blocksSlotClick(boolean, boolean, boolean)} core.
     * 服务端栏位点击否决：从当前界面收集无副作用的事实，再交由纯函数判定。
     */
    public static boolean blocksSlotClick(@Nullable PlayerEntity player, int slotIndex, int button,
                                          @Nullable SlotActionType actionType) {
        if (player == null || actionType == null) {
            return false;
        }
        ScreenHandler handler = player.currentScreenHandler;
        PlayerInventory inventory = player.getInventory();
        Slot clicked = slotIndex >= 0 && slotIndex < handler.slots.size() ? handler.slots.get(slotIndex) : null;
        // For SWAP the button is the PlayerInventory index of the other side: hotbar 0..8, or 40 for the offhand.
        // SWAP 时 button 为另一侧的 PlayerInventory 下标：快捷栏 0..8，副手为 40。
        boolean swapSourcePistol = actionType == SlotActionType.SWAP
                && button >= 0 && button < inventory.size()
                && isPistol(inventory.getStack(button));
        return blocksSlotClick(isPistol(handler.getCursorStack()),
                clicked != null && isPistol(clicked.getStack()), swapSourcePistol);
    }

    /**
     * Pure decision: the pistols are fixed in place for the whole psycho, so any click that would pick up, place,
     * swap, throw, shift-move, drag or clone a pistol is refused. The hotbar lock and the off-hand fire path both rely
     * on the main pistol staying in its hotbar slot and the left pistol in the off hand.
     * 纯函数判定：疯魔期间手枪固定不动，任何会拿起、放下、交换、抛出、Shift 移动、拖拽或复制手枪的点击一律拒绝。
     * 快捷栏锁定与副手开火都依赖主手枪留在快捷栏、左持枪留在副手。
     */
    static boolean blocksSlotClick(boolean cursorPistol, boolean slotPistol, boolean swapSourcePistol) {
        return cursorPistol || slotPistol || swapSourcePistol;
    }

    /** The F-key hand swap would move both pistols; refused while either hand holds one. / 任一手持枪时拒绝 F 键换手。 */
    public static boolean blocksHandSwap(@Nullable ItemStack mainHand, @Nullable ItemStack offHand) {
        return isPistol(mainHand) || isPistol(offHand);
    }
}
