package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the client needs for blackout instinct rules: whether a blackout is on (Wathe keeps it server-only) and, during
 * one, the receiver's own tablet identity links. Each player only ever receives their own links, which they already
 * know from the tablet.
 * 客户端熄灯本能规则所需的数据：是否正在熄灯（Wathe 只在服务端保存），以及熄灯期间接收者自己的平板互认对象。
 * 每名玩家只会收到自己的互认对象，这些本来就能在平板上看到。
 */
public record SyncBlackoutSightS2CPacket(boolean active, List<UUID> linkedPartners) implements CustomPayload {
    /** A round never has more players than this; anything larger is a malformed packet. / 一局玩家数不会超过此值，更大即为畸形包。 */
    private static final int MAX_PARTNERS = 256;
    public static final Identifier PAYLOAD_ID = SparkStrength.id("sync_blackout_sight");
    public static final Id<SyncBlackoutSightS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SyncBlackoutSightS2CPacket> CODEC =
            PacketCodec.of(SyncBlackoutSightS2CPacket::write, SyncBlackoutSightS2CPacket::read);

    public SyncBlackoutSightS2CPacket {
        linkedPartners = linkedPartners == null ? List.of() : List.copyOf(linkedPartners);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(linkedPartners.size());
        for (UUID partner : linkedPartners) {
            buf.writeUuid(partner);
        }
    }

    public static SyncBlackoutSightS2CPacket read(PacketByteBuf buf) {
        boolean active = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_PARTNERS) {
            throw new IllegalArgumentException("Too many linked partners: " + size);
        }
        List<UUID> partners = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            partners.add(buf.readUuid());
        }
        return new SyncBlackoutSightS2CPacket(active, partners);
    }
}
