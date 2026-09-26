package annina.sparkstrength.network.detective;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Tells the client to open the case folder; the folder contents arrive through the case component sync sent first.
 * 通知客户端打开文件夹；文件夹内容由先行发送的案件组件同步提供。
 */
public record OpenDetectiveFolderS2CPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("open_detective_folder");
    public static final Id<OpenDetectiveFolderS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenDetectiveFolderS2CPacket> CODEC =
            PacketCodec.of(OpenDetectiveFolderS2CPacket::write, OpenDetectiveFolderS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static OpenDetectiveFolderS2CPacket read(PacketByteBuf buf) {
        return new OpenDetectiveFolderS2CPacket();
    }
}
