package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Tablet sabotage page: trigger an unlocked sabotage. The server re-checks everything.
 * 平板破坏页：触发一个已解锁的破坏。服务端会重新校验一切。
 */
public record TriggerSabotageC2SPacket(int typeWire) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("trigger_sabotage");
    public static final Id<TriggerSabotageC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, TriggerSabotageC2SPacket> CODEC =
            PacketCodec.of(TriggerSabotageC2SPacket::write, TriggerSabotageC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(typeWire);
    }

    public static TriggerSabotageC2SPacket read(PacketByteBuf buf) {
        return new TriggerSabotageC2SPacket(buf.readVarInt());
    }
}
