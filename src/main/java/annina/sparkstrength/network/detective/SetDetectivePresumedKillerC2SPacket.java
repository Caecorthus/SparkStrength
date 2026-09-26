package annina.sparkstrength.network.detective;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Presumed killer for one case; empty clears it. The server only accepts a current-round participant other than the
 * detective themself.
 * 某起命案的假定凶手，空值表示清除；服务端只接受本局参与者且不能是侦探本人。
 */
public record SetDetectivePresumedKillerC2SPacket(UUID caseId, Optional<UUID> playerUuid) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("set_detective_presumed_killer");
    public static final Id<SetDetectivePresumedKillerC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SetDetectivePresumedKillerC2SPacket> CODEC =
            PacketCodec.of(SetDetectivePresumedKillerC2SPacket::write, SetDetectivePresumedKillerC2SPacket::read);

    public SetDetectivePresumedKillerC2SPacket {
        playerUuid = playerUuid == null ? Optional.empty() : playerUuid;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(caseId);
        buf.writeBoolean(playerUuid.isPresent());
        playerUuid.ifPresent(buf::writeUuid);
    }

    public static SetDetectivePresumedKillerC2SPacket read(PacketByteBuf buf) {
        UUID caseId = buf.readUuid();
        Optional<UUID> playerUuid = buf.readBoolean() ? Optional.of(buf.readUuid()) : Optional.empty();
        return new SetDetectivePresumedKillerC2SPacket(caseId, playerUuid);
    }
}
