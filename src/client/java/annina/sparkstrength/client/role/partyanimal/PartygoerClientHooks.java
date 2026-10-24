package annina.sparkstrength.client.role.partyanimal;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.client.compat.SparkWitchSecondaryKeyCompat;
import annina.sparkstrength.client.renderer.PartygoerEntityRenderer;
import annina.sparkstrength.client.renderer.PartygoerModel;
import annina.sparkstrength.network.partyanimal.PartygoerMenuC2SPacket;
import annina.sparkstrength.network.partyanimal.PartygoerMenuS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.agmas.noellesroles.Noellesroles;

/**
 * Client side of the Partygoer skill: the Role Skill 2 key (SparkWitch's shared key, default N) asks the server for the
 * room picker, the server's answer opens it, and the Partygoer gets its renderer.
 * 派对客技能的客户端部分：“职业技能 2”键（SparkWitch 共享键，默认 N）向服务端请求选房界面，服务端回复后打开界面；并注册派对客渲染器。
 *
 * <p>Like the Vulture's Super Curse, the skill has no key (and no HUD line) without SparkWitch's public key facade.
 * 与秃鹫超级骂一样，没有 SparkWitch 公开按键门面时技能没有按键，也不显示 HUD。</p>
 */
public final class PartygoerClientHooks {
    private static boolean registered;
    private static boolean keyAvailable;

    private PartygoerClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        EntityModelLayerRegistry.registerModelLayer(PartygoerModel.LAYER, PartygoerModel::getTexturedModelData);
        EntityRendererRegistry.register(SparkStrengthEntities.partygoer(), PartygoerEntityRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(PartygoerMenuS2CPacket.ID,
                (payload, context) -> context.client().setScreen(new PartygoerRoomScreen(payload.rooms())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PartygoerCorpseClient::reset));
        if (!FabricLoader.getInstance().isModLoaded("sparkwitch")) {
            return;
        }
        keyAvailable = SparkWitchSecondaryKeyCompat.register(Noellesroles.PARTY_ANIMAL.identifier(),
                PartygoerClientHooks::requestMenu);
        if (!keyAvailable) {
            SparkStrength.LOGGER.warn("Partygoer has no key: SparkWitch Role Skill 2 hook unavailable or taken.");
        }
    }

    /** Whether Role Skill 2 reaches the Partygoer skill. / “职业技能 2”是否能触发派对客技能。 */
    public static boolean isKeyAvailable() {
        return keyAvailable;
    }

    private static void requestMenu() {
        if (ClientPlayNetworking.canSend(PartygoerMenuC2SPacket.ID)) {
            ClientPlayNetworking.send(new PartygoerMenuC2SPacket());
        }
    }
}
