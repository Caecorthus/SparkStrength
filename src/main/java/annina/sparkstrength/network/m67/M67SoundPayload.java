package annina.sparkstrength.network.m67;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;

import java.util.UUID;

/** Server audio identity, never gameplay authority. / 服务端音频标识，不提供玩法权限。 */
public record M67SoundPayload(UUID actorUuid, long playbackToken, byte action, Hand hand,
                              int selectedSlot) implements CustomPayload {
    public static final byte START_EQUIP = 0;
    public static final byte START_PULL = 1;
    public static final byte STOP = 2;
    public static final Id<M67SoundPayload> ID = new Id<>(SparkStrength.id("m67_sound"));
    public static final PacketCodec<RegistryByteBuf, M67SoundPayload> CODEC = new PacketCodec<>() {
        @Override
        public M67SoundPayload decode(RegistryByteBuf buf) {
            return new M67SoundPayload(buf.readUuid(), buf.readLong(), buf.readByte(),
                    buf.readEnumConstant(Hand.class), buf.readVarInt());
        }

        @Override
        public void encode(RegistryByteBuf buf, M67SoundPayload value) {
            buf.writeUuid(value.actorUuid());
            buf.writeLong(value.playbackToken());
            buf.writeByte(value.action());
            buf.writeEnumConstant(value.hand());
            buf.writeVarInt(value.selectedSlot());
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
