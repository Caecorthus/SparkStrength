package annina.sparkstrength.role.serialkiller;

import annina.sparkstrength.SparkStrengthItems;
import net.minecraft.entity.player.PlayerInventory;

/**
 * Hotbar slot the psycho slot locks hold on, shared by both sides (client tick restore, number-key write and mouse
 * wheel; server slot packet and the psycho component ticks). Only the registered main pistol counts, never the left
 * one; slots 0..8, first match wins, -1 when there is none.
 * 疯魔选槽锁要保持的快捷栏槽位，双端共用（客户端 tick 恢复、数字键写槽与滚轮；服务端选槽包与疯魔组件 tick）。
 * 只认已注册的主手连环手枪，不含左手枪；检查 0..8 槽，取第一个匹配，没有则返回 -1。
 */
public final class SerialPistolHotbar {
    private SerialPistolHotbar() {
    }

    public static int findSerialPistolSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getStack(slot).isOf(SparkStrengthItems.serialPistol())) return slot;
        }
        return -1;
    }
}
