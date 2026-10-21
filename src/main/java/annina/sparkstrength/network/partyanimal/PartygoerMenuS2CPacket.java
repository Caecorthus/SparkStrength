package annina.sparkstrength.network.partyanimal;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the Partygoer room picker with this round's room names, in display order.
 * 用本局的房间名（按显示顺序）打开派对客选房界面。
 */
public record PartygoerMenuS2CPacket(List<String> rooms) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("partygoer_menu_open");
    public static final Id<PartygoerMenuS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, PartygoerMenuS2CPacket> CODEC =
            PacketCodec.of(PartygoerMenuS2CPacket::write, PartygoerMenuS2CPacket::read);
    /** Hard cap against a malformed map. / 防止异常地图的硬上限。 */
    private static final int MAX_ROOMS = 128;
    private static final int MAX_NAME = 64;

    public PartygoerMenuS2CPacket {
        rooms = List.copyOf(rooms);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        int count = Math.min(rooms.size(), MAX_ROOMS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeString(rooms.get(i), MAX_NAME);
        }
    }

    public static PartygoerMenuS2CPacket read(PacketByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_ROOMS);
        List<String> rooms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rooms.add(buf.readString(MAX_NAME));
        }
        return new PartygoerMenuS2CPacket(rooms);
    }
}
