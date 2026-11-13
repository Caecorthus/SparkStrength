package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.payloadpolicy.GeneratedPayloadPolicy;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * "Being carried by a Partygoer", shared by the server locks and the client pose and key locks. Like the Kidnapper's
 * victim, a carried player can look around and talk but can't move, attack, use items or switch the hotbar.
 * “正被派对客扛着”，服务端限制与客户端姿势、按键锁共用。与绑架者的受害者一样，被扛着的玩家可以转头和说话，但不能移动、攻击、使用物品或切换快捷栏。
 */
public final class PartygoerCarry {
    /**
     * Action payloads a carried player may not send beyond the daze list ({@code TaotieHeadRules.DAZE_BLOCKED_PAYLOADS}),
     * which lacks them: the SparkWitch Swordfish stab (released from a charge started before the grab) and the
     * Magician's target pick.
     * 除眩晕列表外，被扛着的玩家还不能发送的动作数据包（眩晕列表中没有）：SparkWitch 剑鱼刺击（抓住前开始蓄力、被扛时松开）与魔术师选目标。
     */
    // Generated CARRY_EXTRA column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // 生成的 CARRY_EXTRA 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    public static final Set<Identifier> EXTRA_BLOCKED_PAYLOADS = GeneratedPayloadPolicy.CARRY_EXTRA;

    private PartygoerCarry() {
    }

    public static boolean isCarried(@Nullable Entity entity) {
        return entity != null && entity.getVehicle() instanceof PartygoerEntity;
    }
}
