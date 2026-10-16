package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Per-player sabotage status: whether a lights sabotage is on (darkness, breaker outlines) and whether this player may
 * sabotage (instinct outlines of junction boxes). Sent on change and on join.
 * 按玩家发送的破坏状态：是否正在灯光破坏（黑暗、电闸描边），以及本人能否破坏（本能键下的接线箱描边）。变化时与进服时发送。
 *
 * @param lightsOut   a sabotage outage is on / 正在进行破坏造成的熄灯
 * @param outageId    the outage instance, 0 when none / 熄灯实例，没有时为 0
 * @param modeWire    {@code OutageMode} wire / 熄灯模式
 * @param canSabotage this player may use sabotage right now / 本人当前可以使用破坏
 */
public record SabotageStatusS2CPacket(boolean lightsOut, int outageId, int modeWire, boolean canSabotage)
        implements CustomPayload {
    public static final SabotageStatusS2CPacket NONE = new SabotageStatusS2CPacket(false, 0, 0, false);
    public static final Identifier PAYLOAD_ID = SparkStrength.id("sabotage_status");
    public static final Id<SabotageStatusS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SabotageStatusS2CPacket> CODEC =
            PacketCodec.of(SabotageStatusS2CPacket::write, SabotageStatusS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(lightsOut);
        buf.writeVarInt(outageId);
        buf.writeVarInt(modeWire);
        buf.writeBoolean(canSabotage);
    }

    public static SabotageStatusS2CPacket read(PacketByteBuf buf) {
        return new SabotageStatusS2CPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
