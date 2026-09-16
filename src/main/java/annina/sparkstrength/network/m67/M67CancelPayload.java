package annina.sparkstrength.network.m67;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Sender-only cancellation intent; never carries charge or inventory authority.
 *  仅取消发送者的使用；不携带蓄力或物品栏权限。 */
public record M67CancelPayload() implements CustomPayload {
    public static final M67CancelPayload INSTANCE = new M67CancelPayload();
    public static final Id<M67CancelPayload> ID = new Id<>(SparkStrength.id("m67_cancel"));
    public static final PacketCodec<RegistryByteBuf, M67CancelPayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
