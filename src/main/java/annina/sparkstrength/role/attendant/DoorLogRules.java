package annina.sparkstrength.role.attendant;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure classification of one room-door interaction ({@code SmallDoorBlock#onUse}) into door-log kinds, from the
 * clicked leaf's state before and after the call plus what the player held. No Minecraft types.
 * 纯逻辑：根据被点击门板在 onUse 前后的状态以及玩家手持物，把一次房门交互归类为房门记录类型。不含 Minecraft 类型。
 *
 * <p>Blasts are never derived here: {@code DoorBlockEntity#blast()} raises Wathe's BLAST event, which logs
 * {@link DoorLogKind#BROKEN} on its own, and a door that ends the interaction blasted is never "opened". A jam raised
 * through {@code jam()} during the same interaction is likewise logged by the JAM event, so the diff skips it.
 * 爆破不在此推导：blast() 会触发 Wathe 的 BLAST 事件并由其单独记录 BROKEN，交互结束时处于被破坏状态的门也不算“被打开”。
 * 同一次交互中经 jam() 触发的堵门同样由 JAM 事件记录，差分时跳过。</p>
 */
public final class DoorLogRules {
    /** Room key; only counts when its first lore line names this door, exactly like Wathe. / 房间钥匙；与 Wathe 一致，仅当首行 lore 为本门名时算钥匙。 */
    public static final String WATHE_KEY = "wathe:key";
    public static final String WATHE_LOCKPICK = "wathe:lockpick";
    /**
     * Items that open any room door by design. Matched by registry id so optional mods (SparkWitch) stay soft.
     * 设计上可开任意房门的物品。按注册 id 匹配，使可选模组（SparkWitch）保持软依赖。
     */
    public static final Set<String> UNIVERSAL_KEYS = Set.of(
            "noellesroles:master_key",
            "noellesroles:neutral_master_key",
            "sparkwitch:key_fish"
    );

    private DoorLogRules() {
    }

    /** What the opener held in the main hand when the interaction began. / 交互开始时开门者主手所持之物。 */
    public enum HeldItem {
        KEY,
        LOCKPICK,
        OTHER
    }

    /** Snapshot of the clicked leaf. / 被点击门板的状态快照。 */
    public record DoorState(boolean open, boolean blasted, boolean jammed) {
    }

    /**
     * Categorises the held main-hand item. A Wathe room key for another room is {@link HeldItem#OTHER}: Wathe would
     * refuse it, so a door that still opens was forced (e.g. a Jester in psycho mode holding the wrong key).
     * 归类主手物品。别的房间的 Wathe 钥匙算 OTHER：Wathe 本会拒绝它，门仍被打开即为强行打开（例如疯魔小丑拿着错误钥匙）。
     */
    public static HeldItem heldItem(String itemId, boolean roomKeyMatchesDoor) {
        if (WATHE_KEY.equals(itemId)) {
            return roomKeyMatchesDoor ? HeldItem.KEY : HeldItem.OTHER;
        }
        if (UNIVERSAL_KEYS.contains(itemId)) {
            return HeldItem.KEY;
        }
        if (WATHE_LOCKPICK.equals(itemId)) {
            return HeldItem.LOCKPICK;
        }
        return HeldItem.OTHER;
    }

    /**
     * Kinds to record for one interaction, in a fixed order (REPAIRED, UNJAMMED, opened, JAMMED); usually zero or one.
     * 一次交互应记录的类型，顺序固定（修复、解堵、开门、堵门）；通常为零或一个。
     *
     * <p>Opened = closed before, open after and not blasted after (a Pig God charge inside the interaction opens the
     * door by blasting it: BROKEN only, never FORCED). The opened kind follows the held item: key → KEY_OPENED,
     * lockpick → PICKED, anything else → FORCED (only an ALLOW listener can open a locked room door bare-handed, even
     * a jammed one). Closing, natural jam expiry and repeated states log nothing.
     * 开门 = 交互前关闭、交互后打开且未被破坏（交互中的猪神冲撞以破坏方式开门：只记 BROKEN，绝不记 FORCED）。
     * 开门类型由手持物决定：钥匙 → KEY_OPENED，撬锁器 → PICKED，其他 → FORCED（只有 ALLOW 监听器能空手打开上锁的房门，
     * 包括被堵住的门）。关门、自然解堵与状态不变均不记录。</p>
     */
    public static List<DoorLogKind> classify(DoorState before, DoorState after, HeldItem held,
                                             boolean jamAlreadyLogged) {
        List<DoorLogKind> kinds = new ArrayList<>(2);
        if (before.blasted() && !after.blasted()) {
            kinds.add(DoorLogKind.REPAIRED);
        }
        if (before.jammed() && !after.jammed()) {
            kinds.add(DoorLogKind.UNJAMMED);
        }
        if (!before.open() && after.open() && !after.blasted()) {
            kinds.add(switch (held) {
                case KEY -> DoorLogKind.KEY_OPENED;
                case LOCKPICK -> DoorLogKind.PICKED;
                case OTHER -> DoorLogKind.FORCED;
            });
        }
        if (!before.jammed() && after.jammed() && !jamAlreadyLogged) {
            kinds.add(DoorLogKind.JAMMED);
        }
        return List.copyOf(kinds);
    }
}
