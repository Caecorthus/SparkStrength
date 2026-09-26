package annina.sparkstrength.network.detective;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record SelectDetectiveCaseC2SPacket(UUID caseId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("select_detective_case");
    public static final Id<SelectDetectiveCaseC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SelectDetectiveCaseC2SPacket> CODEC =
            PacketCodec.of(SelectDetectiveCaseC2SPacket::write, SelectDetectiveCaseC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(caseId);
    }

    public static SelectDetectiveCaseC2SPacket read(PacketByteBuf buf) {
        return new SelectDetectiveCaseC2SPacket(buf.readUuid());
    }
}
