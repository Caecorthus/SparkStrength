package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.entity.PartygoerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * "Being carried by a Partygoer", shared by the server locks and the client pose and key locks. Like the Kidnapper's
 * victim, a carried player can look around and talk but can't move, attack, use items or switch the hotbar.
 * “正被派对客扛着”，服务端限制与客户端姿势、按键锁共用。与绑架者的受害者一样，被扛着的玩家可以转头和说话，但不能移动、攻击、使用物品或切换快捷栏。
 */
public final class PartygoerCarry {
    private PartygoerCarry() {
    }

    public static boolean isCarried(@Nullable Entity entity) {
        return entity != null && entity.getVehicle() instanceof PartygoerEntity;
    }
}
