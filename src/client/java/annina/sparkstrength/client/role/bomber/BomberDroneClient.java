package annina.sparkstrength.client.role.bomber;

/**
 * Client entry for the Bomber drones: rendering, flight noise and pilot mode.
 * 炸弹客无人机的客户端入口：渲染、飞行噪音与驾驶模式。
 */
public final class BomberDroneClient {
    private BomberDroneClient() {
    }

    public static void initialize() {
        DroneRenderClient.register();
        DroneSoundClient.register();
        DronePilotClient.register();
    }
}
