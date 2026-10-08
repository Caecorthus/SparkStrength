package annina.sparkstrength.client.role.vulture;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.client.compat.SparkWitchSecondaryKeyCompat;
import annina.sparkstrength.network.vulture.VultureSuperCurseC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.agmas.noellesroles.Noellesroles;

/**
 * 秃鹫“超级骂”的客户端按键挂钩：挂到 SparkWitch 的“职业技能 2”键（默认 N）。
 * Client key hook for the Vulture's Super Curse on SparkWitch's Role Skill 2 key (default N).
 *
 * <p>客户端只发送空请求包；职业、存活、冷却与幼稚词条全部由服务端判定，冷却通过组件同步回来供 HUD 显示。
 * 没有 SparkWitch（或其版本不含公开按键门面）时技能没有按键，HUD 同样隐藏。
 * The client only sends an empty request; the server decides role, liveness, cooldown and the Childish variant, and
 * syncs the cooldown back for the HUD. Without SparkWitch (or its public key facade) the skill has no key and no HUD.</p>
 */
public final class VultureSuperCurseClientHooks {
    private static boolean registered;
    private static boolean keyAvailable;

    private VultureSuperCurseClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        if (!FabricLoader.getInstance().isModLoaded("sparkwitch")) {
            return;
        }
        // SparkWitch dispatches by the raw Wathe role id. / SparkWitch 按原始 Wathe 职业 id 分发。
        keyAvailable = SparkWitchSecondaryKeyCompat.register(
                Noellesroles.VULTURE.identifier(),
                VultureSuperCurseClientHooks::requestSuperCurse
        );
        if (!keyAvailable) {
            SparkStrength.LOGGER.warn("Vulture Super Curse has no key: SparkWitch Role Skill 2 hook unavailable or taken.");
        }
    }

    /** Whether Role Skill 2 reaches the Super Curse. / “职业技能 2”是否能触发超级骂。 */
    public static boolean isKeyAvailable() {
        return keyAvailable;
    }

    private static void requestSuperCurse() {
        // A server without SparkStrength's receiver never gets the packet. / 服务端未注册接收器时不发送。
        if (ClientPlayNetworking.canSend(VultureSuperCurseC2SPacket.ID)) {
            ClientPlayNetworking.send(new VultureSuperCurseC2SPacket());
        }
    }
}
