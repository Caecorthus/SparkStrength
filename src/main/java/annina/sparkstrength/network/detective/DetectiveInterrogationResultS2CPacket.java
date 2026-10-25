package annina.sparkstrength.network.detective;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.detective.DetectiveCaseRules.InvestigationOutcome;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Answer to one remote interrogation, for the roster: the case it was asked about (null for NO_CASE), the uuid the
 * detective picked, the outcome and, for a recorded clue, the distance (0 otherwise). A recorded clue also arrives
 * through the folder sync; this packet is what lets the roster show "not present", which the folder does not keep.
 * 一次远程审问的回应，供名单使用：所问的命案（NO_CASE 时为 null）、侦探所选的 UUID、结果，以及记录线索时的距离（其余为 0）。
 * 记录的线索也会经文件夹同步送达；名单靠这个包才能显示文件夹不保存的“不在场”。
 */
public record DetectiveInterrogationResultS2CPacket(@Nullable UUID caseId, UUID playerUuid, InvestigationOutcome outcome,
                                                    int distanceBlocks) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("detective_interrogation_result");
    public static final Id<DetectiveInterrogationResultS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, DetectiveInterrogationResultS2CPacket> CODEC =
            PacketCodec.of(DetectiveInterrogationResultS2CPacket::write, DetectiveInterrogationResultS2CPacket::read);

    public DetectiveInterrogationResultS2CPacket {
        distanceBlocks = Math.max(0, distanceBlocks);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(caseId != null);
        if (caseId != null) {
            buf.writeUuid(caseId);
        }
        buf.writeUuid(playerUuid);
        buf.writeVarInt(outcome.ordinal());
        buf.writeVarInt(distanceBlocks);
    }

    public static DetectiveInterrogationResultS2CPacket read(PacketByteBuf buf) {
        UUID caseId = buf.readBoolean() ? buf.readUuid() : null;
        UUID playerUuid = buf.readUuid();
        int ordinal = buf.readVarInt();
        InvestigationOutcome[] outcomes = InvestigationOutcome.values();
        if (ordinal < 0 || ordinal >= outcomes.length) {
            throw new DecoderException("Unknown detective interrogation outcome " + ordinal);
        }
        return new DetectiveInterrogationResultS2CPacket(caseId, playerUuid, outcomes[ordinal], buf.readVarInt());
    }
}
