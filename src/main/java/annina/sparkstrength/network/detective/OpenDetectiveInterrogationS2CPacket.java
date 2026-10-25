package annina.sparkstrength.network.detective;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Tells the client to open the magnifier's remote interrogation roster; like the folder, the case data arrives
 * through the case component sync sent first.
 * 通知客户端打开放大镜的远程审问名单；与文件夹相同，命案数据由先行发送的案件组件同步提供。
 */
public record OpenDetectiveInterrogationS2CPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("open_detective_interrogation");
    public static final Id<OpenDetectiveInterrogationS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenDetectiveInterrogationS2CPacket> CODEC =
            PacketCodec.of(OpenDetectiveInterrogationS2CPacket::write, OpenDetectiveInterrogationS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static OpenDetectiveInterrogationS2CPacket read(PacketByteBuf buf) {
        return new OpenDetectiveInterrogationS2CPacket();
    }
}
