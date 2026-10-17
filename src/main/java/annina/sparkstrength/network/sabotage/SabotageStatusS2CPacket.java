package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player sabotage status: whether a lights sabotage is on (darkness, breaker outlines), whether this player may
 * sabotage (instinct outlines of junction boxes), and the engine sabotage (train motion, round timer freeze, long-range
 * unit outlines). Sent on change and on join; the record's equality drives "on change", so the unit list is copied into
 * an immutable list.
 * 按玩家发送的破坏状态：是否正在灯光破坏（黑暗、电闸描边）、本人能否破坏（本能键下的接线箱描边），以及动力破坏（列车运动、回合
 * 倒计时冻结、远距离装置描边）。变化时与进服时发送；「变化」靠 record 的相等判断，所以装置列表会复制成不可变列表。
 *
 * @param lightsOut         a sabotage outage is on / 正在进行破坏造成的熄灯
 * @param outageId          the outage instance, 0 when none / 熄灯实例，没有时为 0
 * @param modeWire          {@code OutageMode} wire / 熄灯模式
 * @param canSabotage       this player may use sabotage right now / 本人当前可以使用破坏
 * @param enginePhaseWire   {@code EnginePhase} wire, 0 when no engine sabotage is on / 动力破坏阶段，没有时为 0
 * @param engineTriggeredAt world tick the engine sabotage was triggered, 0 when none is on; the phase deadlines follow
 *                          from it with {@code EngineRules} / 动力破坏触发时的世界 tick，没有时为 0；各阶段的截止时刻可用
 *                          EngineRules 由它算出
 * @param engineUnits       every registered engine unit while an engine sabotage is on, empty otherwise, so units
 *                          beyond the render distance can be outlined / 动力破坏进行中时为全部已登记的动力装置，否则为空，
 *                          用于给渲染距离外的装置描边
 */
public record SabotageStatusS2CPacket(boolean lightsOut, int outageId, int modeWire, boolean canSabotage,
                                      int enginePhaseWire, long engineTriggeredAt, List<BlockPos> engineUnits)
        implements CustomPayload {
    public static final SabotageStatusS2CPacket NONE = new SabotageStatusS2CPacket(false, 0, 0, false, 0, 0L,
            List.of());
    /**
     * Most engine units the packet carries, far more than any map places; a bad count cannot make the client allocate
     * wildly. Units past it are still repairable but get no long-range outline, so the circuit configurator warns a map
     * with more.
     * 包里最多携带的动力装置数，远多于任何地图会放的数量；错误的数量不会让客户端乱分配内存。超出的装置照样能修，只是没有
     * 远距离描边，所以线路配置器会对超过它的地图发出提醒。
     */
    public static final int MAX_ENGINE_UNITS = 256;
    public static final Identifier PAYLOAD_ID = SparkStrength.id("sabotage_status");
    public static final Id<SabotageStatusS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SabotageStatusS2CPacket> CODEC =
            PacketCodec.of(SabotageStatusS2CPacket::write, SabotageStatusS2CPacket::read);

    public SabotageStatusS2CPacket {
        engineUnits = engineUnits == null ? List.of() : List.copyOf(engineUnits);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(lightsOut);
        buf.writeVarInt(outageId);
        buf.writeVarInt(modeWire);
        buf.writeBoolean(canSabotage);
        buf.writeVarInt(enginePhaseWire);
        buf.writeLong(engineTriggeredAt);
        int count = Math.min(engineUnits.size(), MAX_ENGINE_UNITS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeBlockPos(engineUnits.get(i));
        }
    }

    public static SabotageStatusS2CPacket read(PacketByteBuf buf) {
        boolean lightsOut = buf.readBoolean();
        int outageId = buf.readVarInt();
        int modeWire = buf.readVarInt();
        boolean canSabotage = buf.readBoolean();
        int enginePhaseWire = buf.readVarInt();
        long engineTriggeredAt = buf.readLong();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENGINE_UNITS) {
            throw new IllegalArgumentException("Too many engine units: " + count);
        }
        List<BlockPos> engineUnits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            engineUnits.add(buf.readBlockPos());
        }
        return new SabotageStatusS2CPacket(lightsOut, outageId, modeWire, canSabotage, enginePhaseWire,
                engineTriggeredAt, engineUnits);
    }
}
