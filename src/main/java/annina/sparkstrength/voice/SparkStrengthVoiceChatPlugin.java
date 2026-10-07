package annina.sparkstrength.voice;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.morphling.MorphMarkPlayerComponent;
import annina.sparkstrength.role.jester.JesterMomentService;
import annina.sparkstrength.role.pathogen.PathogenVisibility;
import annina.sparkstrength.component.reporter.ReporterCommunicationComponent;
import annina.sparkstrength.role.morphling.MorphlingService;
import annina.sparkstrength.role.reporter.ReporterCommunicationManager;
import annina.sparkstrength.role.reporter.ReporterCommunicationService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 变形试剂的语音伪装。
 *
 * <p>规则分两段：
 * 1. 正在试剂变形成别人的玩家，自己的麦克风包直接取消，避免“外观像 B、声音却从 A 本体发出”。
 * 2. 被采样目标 B 说话时，复制一份同样的语音数据，但把声源实体改成所有正在变成 B 的玩家 A。
 * 这样 B 的原始语音仍然保留，同时 A 附近的玩家也会听见“从 A 身上传来的 B 声音”。</p>
 *
 * <p>小丑时刻：被触发时刻的小丑从中枪起到时刻结束既不能说话也听不到（见 {@link JesterMomentService#isVoiceBlocked}）。</p>
 *
 * <p>病原体：被 T病毒复活的存活病原体不能说话，但仍能听见（见 {@link PathogenVisibility#isMuted}）。</p>
 */
public final class SparkStrengthVoiceChatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return SparkStrength.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // Highest priority: Simple Voice Chat stops dispatching once an event is cancelled, so a muted Jester's voice
        // never reaches Wathe's walkie-talkie relay, the Noisemaker broadcast or the morph relay below.
        // 最高优先级：事件被取消后 Simple Voice Chat 不再分发，因此静音小丑的声音不会进入 Wathe 对讲机、喧哗者广播或下方的伪装转发。
        registration.registerEvent(MicrophonePacketEvent.class, this::blockJesterMomentSpeaker, Integer.MAX_VALUE);
        // A living Pathogen revived by a T-Virus never speaks; it still hears. / T病毒复活的存活病原体不能说话，但仍能听见。
        registration.registerEvent(MicrophonePacketEvent.class, this::blockMutedPathogenSpeaker, Integer.MAX_VALUE);
        // Every server-to-client voice packet, including relays other plugins build, passes these per-listener events.
        // 每个服务端发往客户端的语音包（包括其他插件构造的转发）都会经过这些逐接收者事件。
        registration.registerEvent(EntitySoundPacketEvent.class, this::blockJesterMomentListener, Integer.MAX_VALUE);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::blockJesterMomentListener, Integer.MAX_VALUE);
        registration.registerEvent(StaticSoundPacketEvent.class, this::blockJesterMomentListener, Integer.MAX_VALUE);
        registration.registerEvent(MicrophonePacketEvent.class, this::handleMicrophonePacket);
        VoicechatPlugin.super.registerEvents(registration);
    }

    private void blockJesterMomentSpeaker(MicrophonePacketEvent event) {
        if (JesterMomentService.isVoiceBlocked(resolveServerPlayer(event.getSenderConnection()))) {
            event.cancel();
        }
    }

    private void blockMutedPathogenSpeaker(MicrophonePacketEvent event) {
        if (PathogenVisibility.isMuted(resolveServerPlayer(event.getSenderConnection()))) {
            event.cancel();
        }
    }

    private void blockJesterMomentListener(PacketEvent<?> event) {
        if (JesterMomentService.isVoiceBlocked(resolveServerPlayer(event.getReceiverConnection()))) {
            event.cancel();
        }
    }

    private void handleMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatServerApi api = event.getVoicechat();
        ServerPlayerEntity sender = resolveServerPlayer(event.getSenderConnection());
        if (sender == null) {
            return;
        }

        MorphMarkPlayerComponent senderMark = MorphMarkPlayerComponent.KEY.get(sender);
        if (senderMark.isActive()) {
            // 伪装者自己的声音不应该按真实身份播出；真正的“伪装声音”由被采样目标说话时转发。
            event.cancel();
            return;
        }

        /*
         * 记者通讯是“额外复制一份语音”的能力，不能被变形怪的伪装列表早退挡住。
         * 因此先无条件尝试记者转发，再继续处理可能存在的变形伪装转发。
         */
        relayReporterCommunicationVoice(api, event, sender);

        List<ServerPlayerEntity> disguisedPlayers = MorphlingService.findActivePlayersDisguisedAs(sender);
        for (ServerPlayerEntity disguisedPlayer : disguisedPlayers) {
            relayVoiceAsDisguisedPlayer(api, event, sender, disguisedPlayer);
        }
    }

    private void relayVoiceAsDisguisedPlayer(
            VoicechatServerApi api,
            MicrophonePacketEvent event,
            ServerPlayerEntity originalSpeaker,
            ServerPlayerEntity disguisedPlayer
    ) {
        EntitySoundPacket.Builder<?> builder = event.getPacket()
                .entitySoundPacketBuilder()
                .entityUuid(disguisedPlayer.getUuid())
                .whispering(event.getPacket().isWhispering())
                .distance((float) api.getVoiceChatDistance());

        /*
         * simple-voice-chat 2.6.x 的公开 entityUuid(...) 在部分路径里不会真正改到底层 sender。
         * 参考 NoellesRoles 自改版的处理，这里在 build 前同步修正 builder 内部 sender/channelId，
         * 确保客户端看到的声源实体、说话图标和空间位置都落到伪装者 A 身上。
         */
        if (!retargetEntitySoundBuilder(
                builder,
                getMorphVoiceChannelId(originalSpeaker, disguisedPlayer),
                disguisedPlayer.getUuid()
        )) {
            return;
        }

        EntitySoundPacket redirectedPacket = builder.build();
        for (ServerPlayerEntity recipient : originalSpeaker.getServer().getPlayerManager().getPlayerList()) {
            VoicechatConnection connection = api.getConnectionOf(recipient.getUuid());
            if (connection != null) {
                // 不跳过原说话者 B：如果 B 在 A 的声音范围内，也能听见自己被 A “复读”的那份伪装语音。
                api.sendEntitySoundPacketTo(connection, redirectedPacket);
            }
        }
    }

    private UUID getMorphVoiceChannelId(ServerPlayerEntity originalSpeaker, ServerPlayerEntity disguisedPlayer) {
        return UUID.nameUUIDFromBytes(
                (SparkStrength.MOD_ID + ":morph_voice:" + originalSpeaker.getUuid() + ":" + disguisedPlayer.getUuid())
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private void relayReporterCommunicationVoice(
            VoicechatServerApi api,
            MicrophonePacketEvent event,
            ServerPlayerEntity sender
    ) {
        if (!ReporterCommunicationService.isCommunicableEndpoint(sender)) {
            return;
        }

        /*
         * 记者通讯的语音要求是“远距离也能直接听到”，并且需要让饕餮肚子里的人
         * 在被接线/广播时越过胃部语音隔离。StaticSoundPacket 不绑定世界位置，
         * 手动发给目标连接即可，正好复刻自改版接线员的无距离衰减效果。
         */
        StaticSoundPacket redirectedPacket = event.getPacket().staticSoundPacketBuilder().build();
        relayReporterConnectionVoice(api, sender, redirectedPacket);
        relayReporterBroadcastVoice(api, sender, redirectedPacket);
    }

    private void relayReporterConnectionVoice(
            VoicechatServerApi api,
            ServerPlayerEntity sender,
            StaticSoundPacket redirectedPacket
    ) {
        for (ServerPlayerEntity possibleReporter : sender.getServer().getPlayerManager().getPlayerList()) {
            ReporterCommunicationComponent component = ReporterCommunicationComponent.KEY.get(possibleReporter);
            ReporterCommunicationManager.UUIDPair pair = ReporterCommunicationManager.getConnectionPair(component);
            if (pair == null) {
                continue;
            }

            UUID recipientUuid = null;
            if (sender.getUuid().equals(pair.first())) {
                recipientUuid = pair.second();
            } else if (sender.getUuid().equals(pair.second())) {
                recipientUuid = pair.first();
            }

            if (recipientUuid == null) {
                continue;
            }

            ServerPlayerEntity recipient = sender.getServer().getPlayerManager().getPlayer(recipientUuid);
            if (!ReporterCommunicationService.isCommunicableEndpoint(recipient)) {
                continue;
            }

            VoicechatConnection connection = api.getConnectionOf(recipientUuid);
            if (connection != null) {
                api.sendStaticSoundPacketTo(connection, redirectedPacket);
            }
        }
    }

    private void relayReporterBroadcastVoice(
            VoicechatServerApi api,
            ServerPlayerEntity sender,
            StaticSoundPacket redirectedPacket
    ) {
        for (ServerPlayerEntity possibleReporter : sender.getServer().getPlayerManager().getPlayerList()) {
            ReporterCommunicationComponent component = ReporterCommunicationComponent.KEY.get(possibleReporter);
            if (!component.hasActiveBroadcast() || component.getBroadcastTarget() == null) {
                continue;
            }
            if (!sender.getUuid().equals(component.getBroadcastTarget())) {
                continue;
            }

            for (ServerPlayerEntity recipient : sender.getServer().getPlayerManager().getPlayerList()) {
                if (recipient.getUuid().equals(sender.getUuid())) {
                    continue;
                }
                // 广播采访按需求覆盖所有本局在线玩家，不按存活状态过滤。
                if (!ReporterCommunicationService.isInGameOnlinePlayer(sender, recipient)) {
                    continue;
                }
                VoicechatConnection connection = api.getConnectionOf(recipient.getUuid());
                if (connection != null) {
                    api.sendStaticSoundPacketTo(connection, redirectedPacket);
                }
            }
        }
    }

    private boolean retargetEntitySoundBuilder(
            EntitySoundPacket.Builder<?> builder,
            UUID channelId,
            UUID senderUuid
    ) {
        try {
            setFieldRecursively(builder, "sender", senderUuid);
            setFieldRecursively(builder, "channelId", channelId);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void setFieldRecursively(Object instance, String fieldName, Object value) throws ReflectiveOperationException {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(instance, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private @Nullable ServerPlayerEntity resolveServerPlayer(@Nullable VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null || connection.getPlayer().getPlayer() == null) {
            return null;
        }

        Object rawPlayer = connection.getPlayer().getPlayer();
        return rawPlayer instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
    }
}
