package annina.sparkstrength.network.partyanimal;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The Party Animal confirmed the picker: summon the Partygoer in {@code spawnRoom}, bound for {@code destinationRoom}.
 * Rooms are sent by name; the server looks them up again and re-checks every gate.
 * 派对狂确认了选房：在 spawnRoom 召唤派对客，终点为 destinationRoom。房间按名称发送；服务端重新查找并重新检查所有门槛。
 */
public record PartygoerSummonC2SPacket(String spawnRoom, String destinationRoom) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("partygoer_summon");
    public static final Id<PartygoerSummonC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, PartygoerSummonC2SPacket> CODEC =
            PacketCodec.of(PartygoerSummonC2SPacket::write, PartygoerSummonC2SPacket::read);
    private static final int MAX_NAME = 64;

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(spawnRoom, MAX_NAME);
        buf.writeString(destinationRoom, MAX_NAME);
    }

    public static PartygoerSummonC2SPacket read(PacketByteBuf buf) {
        return new PartygoerSummonC2SPacket(buf.readString(MAX_NAME), buf.readString(MAX_NAME));
    }
}
