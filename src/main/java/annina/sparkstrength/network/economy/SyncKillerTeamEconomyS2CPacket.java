package annina.sparkstrength.network.economy;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-authoritative private team-wallet snapshot; clients receive no membership data.
 * 服务端权威的私有团队钱包快照；客户端不会收到成员信息。
 */
public record SyncKillerTeamEconomyS2CPacket(boolean visible, int balance) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("sync_killer_team_economy");
    public static final Id<SyncKillerTeamEconomyS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SyncKillerTeamEconomyS2CPacket> CODEC =
            PacketCodec.of(SyncKillerTeamEconomyS2CPacket::write, SyncKillerTeamEconomyS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(visible);
        buf.writeInt(balance);
    }

    public static SyncKillerTeamEconomyS2CPacket read(PacketByteBuf buf) {
        return new SyncKillerTeamEconomyS2CPacket(buf.readBoolean(), buf.readInt());
    }
}
