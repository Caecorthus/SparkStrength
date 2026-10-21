package annina.sparkstrength.network.partyanimal;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The Taotie pressed the NoellesRoles ability key with a Partygoer under the vanilla crosshair: swallow entity
 * {@code entityId}. Only the id is sent; the server resolves it again and re-checks every gate.
 * 饕餮在原版准星对着派对客时按下 NoellesRoles 技能键：吞下实体 entityId。只发送 id；服务端重新解析并重新检查所有门槛。
 */
public record PartygoerSwallowC2SPacket(int entityId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("partygoer_swallow");
    public static final Id<PartygoerSwallowC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, PartygoerSwallowC2SPacket> CODEC =
            PacketCodec.of(PartygoerSwallowC2SPacket::write, PartygoerSwallowC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static PartygoerSwallowC2SPacket read(PacketByteBuf buf) {
        return new PartygoerSwallowC2SPacket(buf.readVarInt());
    }
}
