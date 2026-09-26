package annina.sparkstrength.network.detective;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Notes edit request. The wire read is capped at NOTES_WIRE_MAX_CHARS (a longer string is a decode error);
 * the server still sanitizes to NOTES_MAX_LENGTH before storing. The writer truncates so a client never trips the cap.
 * 笔记修改请求。读取上限为 NOTES_WIRE_MAX_CHARS（超长视为解码错误）；服务端存储前仍会清洗到 256 字符。
 * 写入端会先截断，正常客户端不会触发上限。
 */
public record UpdateDetectiveCaseNotesC2SPacket(UUID caseId, String notes) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("update_detective_case_notes");
    public static final Id<UpdateDetectiveCaseNotesC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UpdateDetectiveCaseNotesC2SPacket> CODEC =
            PacketCodec.of(UpdateDetectiveCaseNotesC2SPacket::write, UpdateDetectiveCaseNotesC2SPacket::read);

    public UpdateDetectiveCaseNotesC2SPacket {
        notes = notes == null ? "" : notes;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(caseId);
        buf.writeString(
                DetectiveCaseRules.truncate(notes, DetectiveCaseRules.NOTES_WIRE_MAX_CHARS),
                DetectiveCaseRules.NOTES_WIRE_MAX_CHARS
        );
    }

    public static UpdateDetectiveCaseNotesC2SPacket read(PacketByteBuf buf) {
        return new UpdateDetectiveCaseNotesC2SPacket(buf.readUuid(), buf.readString(DetectiveCaseRules.NOTES_WIRE_MAX_CHARS));
    }
}
