package annina.sparkstrength.network.detective;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Remote interrogation of one participant, picked from the magnifier roster, against the selected case. The server
 * only accepts a current-round participant other than the detective themself and answers with
 * {@link DetectiveInterrogationResultS2CPacket}.
 * 从放大镜名单中选择一名参与者，就当前调查的命案远程审问；服务端只接受本局参与者且不能是侦探本人，
 * 并以 DetectiveInterrogationResultS2CPacket 回应。
 */
public record InterrogateDetectiveSuspectC2SPacket(UUID playerUuid) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("interrogate_detective_suspect");
    public static final Id<InterrogateDetectiveSuspectC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, InterrogateDetectiveSuspectC2SPacket> CODEC =
            PacketCodec.of(InterrogateDetectiveSuspectC2SPacket::write, InterrogateDetectiveSuspectC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(playerUuid);
    }

    public static InterrogateDetectiveSuspectC2SPacket read(PacketByteBuf buf) {
        return new InterrogateDetectiveSuspectC2SPacket(buf.readUuid());
    }
}
