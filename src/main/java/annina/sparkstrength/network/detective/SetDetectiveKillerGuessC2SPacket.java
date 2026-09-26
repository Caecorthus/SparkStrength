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
 * Killer-role guess for one case; empty clears it. The server only accepts non-special roles from WatheRoles.ROLES.
 * 某起命案的凶手身份推测，空值表示清除；服务端只接受 WatheRoles.ROLES 中的非特殊角色。
 */
public record SetDetectiveKillerGuessC2SPacket(UUID caseId, Optional<Identifier> roleId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("set_detective_killer_guess");
    public static final Id<SetDetectiveKillerGuessC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SetDetectiveKillerGuessC2SPacket> CODEC =
            PacketCodec.of(SetDetectiveKillerGuessC2SPacket::write, SetDetectiveKillerGuessC2SPacket::read);

    public SetDetectiveKillerGuessC2SPacket {
        roleId = roleId == null ? Optional.empty() : roleId;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(caseId);
        buf.writeBoolean(roleId.isPresent());
        roleId.ifPresent(buf::writeIdentifier);
    }

    public static SetDetectiveKillerGuessC2SPacket read(PacketByteBuf buf) {
        UUID caseId = buf.readUuid();
        Optional<Identifier> roleId = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.empty();
        return new SetDetectiveKillerGuessC2SPacket(caseId, roleId);
    }
}
