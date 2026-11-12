package annina.sparkstrength;

import annina.sparkstrength.role.bandit.BanditRevolverState;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * SparkStrength item data components. Ids are a stable save/network contract; components sync to clients for
 * tooltips and item bars.
 * SparkStrength 物品数据组件。id 是稳定的存档与网络契约；组件会同步到客户端，用于提示文本与物品条。
 */
public final class SparkStrengthDataComponents {
    public static final Identifier DRONE_CHARGE_ID = SparkStrength.id("drone_charge");
    public static final Identifier DRONE_PAYLOAD_ID = SparkStrength.id("drone_payload");
    public static final Identifier BANDIT_REVOLVER_ID = SparkStrength.id("bandit_revolver");
    /** Drone battery in basis points (see DroneRules.CHARGE_MAX). / 无人机电量，万分比（见 DroneRules.CHARGE_MAX）。 */
    private static ComponentType<Integer> droneCharge;
    /** Grenade drone has a bound M67. / 投弹无人机已挂载 M67。 */
    private static ComponentType<Boolean> dronePayload;
    /** Parts and cylinder of a Bandit revolver. / 强盗改装左轮的配件与弹巢。 */
    private static ComponentType<BanditRevolverState> banditRevolver;
    private static boolean registered;

    private SparkStrengthDataComponents() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        droneCharge = Registry.register(Registries.DATA_COMPONENT_TYPE, DRONE_CHARGE_ID,
                ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());
        dronePayload = Registry.register(Registries.DATA_COMPONENT_TYPE, DRONE_PAYLOAD_ID,
                ComponentType.<Boolean>builder().codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());
        banditRevolver = Registry.register(Registries.DATA_COMPONENT_TYPE, BANDIT_REVOLVER_ID,
                ComponentType.<BanditRevolverState>builder().codec(BanditRevolverState.CODEC)
                        .packetCodec(BanditRevolverState.PACKET_CODEC).build());
        registered = true;
    }

    public static ComponentType<Integer> droneCharge() {
        if (droneCharge == null) {
            throw new IllegalStateException("SparkStrength data components are not registered yet");
        }
        return droneCharge;
    }

    public static ComponentType<Boolean> dronePayload() {
        if (dronePayload == null) {
            throw new IllegalStateException("SparkStrength data components are not registered yet");
        }
        return dronePayload;
    }

    public static ComponentType<BanditRevolverState> banditRevolver() {
        if (banditRevolver == null) {
            throw new IllegalStateException("SparkStrength data components are not registered yet");
        }
        return banditRevolver;
    }
}
