package annina.sparkstrength.role.serialkiller;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.agmas.noellesroles.Noellesroles;

/**
 * Server lifecycle of the serial pistols outside psycho start: a per-tick sweep strips every copy from anyone who is
 * not a living, playing Serial Killer in psycho (dead, round over or reset, role changed, reconnected after psycho,
 * or any other holder), and the pistols cannot be handed to item frames, armor stands or allays.
 * 连环手枪在疯魔开始之外的服务端生命周期：每 tick 清扫会从所有不是“存活、在局、疯魔中的连环杀手”的玩家身上收走全部副本
 * （死亡、局终或重置、换身份、疯魔结束后重连，以及任何其他持有者）；手枪也不能交给物品展示框、盔甲架或悦灵。
 */
public final class SerialPistolGuardService {
    private static boolean registered;

    private SerialPistolGuardService() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (SerialPistolInventoryRules.sweeps(player.isCreative(), mayHold(player))) {
                    removeAll(player);
                }
            }
        });
        // Both sides: the client stops before sending, the server refuses a forged packet. Decorated pots are guarded
        // on the block itself (DecoratedPotBlockSerialPistolMixin) so the pistol still fires there.
        // 双端生效：客户端在发包前拦截，服务端拒绝伪造的数据包。饰纹陶罐在方块本身拦截，因此在陶罐前仍可开火。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                SerialPistolInventoryRules.blocksEntityUse(player.getStackInHand(hand), entity)
                        ? ActionResult.FAIL : ActionResult.PASS);
    }

    /** Playing, alive, exactly the Serial Killer and in psycho. / 正在对局、存活、恰好是连环杀手且处于疯魔。 */
    public static boolean mayHold(PlayerEntity player) {
        return SerialPistolInventoryRules.isEntitled(
                GameFunctions.isPlayerPlayingAndAlive(player),
                GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.SERIAL_KILLER),
                PlayerPsychoComponent.KEY.get(player).getPsychoTicks());
    }

    /**
     * Removes every pistol from inventory 0..40, the cursor and the open handler's foreign slots (crafting grid,
     * containers). Cheap when nothing is wrong: a slot scan with no writes.
     * 从背包 0..40、光标及已打开界面的外部栏位（合成格、容器）中移除所有手枪。一切正常时只扫描、不写入。
     */
    public static void removeAll(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (SerialPistolInventoryRules.isPistol(inventory.getStack(slot))) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        for (ScreenHandler handler : new ScreenHandler[]{player.playerScreenHandler, player.currentScreenHandler}) {
            if (SerialPistolInventoryRules.isPistol(handler.getCursorStack())) {
                handler.setCursorStack(ItemStack.EMPTY);
                changed = true;
            }
            for (Slot slot : handler.slots) {
                if (slot.inventory != inventory && SerialPistolInventoryRules.isPistol(slot.getStack())) {
                    slot.setStack(ItemStack.EMPTY);
                    changed = true;
                }
            }
        }
        if (changed) {
            inventory.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
