package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.item.FlashlightItem;
import annina.sparkstrength.role.attendant.FlashlightBeamRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client facade for every lit flashlight this client can see (any player holding one, no role check).
 * 本客户端可见的所有已开启手电筒的客户端门面（任何手持者都算，不检查身份）。
 *
 * <p>Authority: purely visual. The server only toggles the item's custom model data; every client derives the lights
 * from the synced held stacks and casts its own occlusion. Spectators never emit light.
 * 权限：纯视觉。服务端只切换物品的自定义模型数据；各客户端根据同步的手持物品自行推导光源并计算遮挡。旁观者不发光。</p>
 */
public final class FlashlightLights {
    public static final int MAX_LIGHTS = 4;
    /**
     * Block entities sample a point pulled this far toward each light, so their own collision box (a chest, a closed
     * door) does not shadow them. 方块实体的采样点朝光源方向拉近此距离，避免被自身碰撞箱（箱子、关闭的门）遮挡。
     */
    private static final double BLOCK_ENTITY_SAMPLE_PULL = 0.5;
    private static final int NO_OWNER = Integer.MIN_VALUE;
    private static final double RANGE_SQ = FlashlightBeamRules.RANGE_BLOCKS * FlashlightBeamRules.RANGE_BLOCKS;
    private static final double COS_SPILL = FlashlightBeamRules.cosSpill();

    private static final List<TrackedFlashlight> TRACKED = new ArrayList<>(MAX_LIGHTS);
    private static final FlashlightRayCaster CASTER = new FlashlightRayCaster();
    private static final AbstractClientPlayerEntity[] NEAREST = new AbstractClientPlayerEntity[MAX_LIGHTS];
    private static final double[] NEAREST_DISTANCE_SQ = new double[MAX_LIGHTS];
    private static final FlashlightLight[] FRAME_SCRATCH = new FlashlightLight[MAX_LIGHTS];
    private static final double[] FRAME_DISTANCE_SQ = new double[MAX_LIGHTS];

    private static boolean registered;
    private static @Nullable ClientWorld trackedWorld;
    private static long tickCount;

    // Per-frame cache: entity and block-entity lighting call collect once per rendered object.
    // 逐帧缓存：实体与方块实体照明会对每个渲染对象调用 collect。
    private static List<FlashlightLight> frameLights = List.of();
    private static long frameTick = Long.MIN_VALUE;
    private static float frameTickDelta = Float.NaN;
    private static @Nullable Vec3d frameCameraPos;
    private static float frameCameraYaw;
    private static float frameCameraPitch;

    private FlashlightLights() {
    }

    /** Client init: tick-time ray casting and disconnect cleanup. / 客户端初始化：tick 射线投射与断线清理。 */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(FlashlightLights::tick);
        // Ray map textures are GL objects; release them on the client thread. / 射线图纹理是 GL 对象，在客户端线程释放。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FlashlightLights::clear));
    }

    /** Render thread: lights for this frame, nearest to the camera first, at most MAX_LIGHTS. / 渲染线程：本帧光源，按距相机由近到远。 */
    public static List<FlashlightLight> collect(MinecraftClient client, float tickDelta) {
        if (TRACKED.isEmpty()) {
            return List.of();
        }
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        float cameraYaw = camera.getYaw();
        float cameraPitch = camera.getPitch();
        if (frameTick == tickCount
                && Float.compare(frameTickDelta, tickDelta) == 0
                && frameCameraPos == cameraPos
                && Float.compare(frameCameraYaw, cameraYaw) == 0
                && Float.compare(frameCameraPitch, cameraPitch) == 0) {
            return frameLights;
        }

        Entity focused = camera.getFocusedEntity();
        boolean firstPerson = camera.isReady() && !camera.isThirdPerson();
        int count = 0;
        for (TrackedFlashlight tracked : TRACKED) {
            FlashlightLight light = tracked.frameLight(camera, firstPerson && focused == tracked.player, tickDelta);
            double distanceSq = light.origin().squaredDistanceTo(cameraPos);
            int slot = count++;
            while (slot > 0 && FRAME_DISTANCE_SQ[slot - 1] > distanceSq) {
                FRAME_SCRATCH[slot] = FRAME_SCRATCH[slot - 1];
                FRAME_DISTANCE_SQ[slot] = FRAME_DISTANCE_SQ[slot - 1];
                slot--;
            }
            FRAME_SCRATCH[slot] = light;
            FRAME_DISTANCE_SQ[slot] = distanceSq;
        }
        List<FlashlightLight> lights = List.of(Arrays.copyOf(FRAME_SCRATCH, count));
        Arrays.fill(FRAME_SCRATCH, null);

        frameLights = lights;
        frameTick = tickCount;
        frameTickDelta = tickDelta;
        frameCameraPos = cameraPos;
        frameCameraYaw = cameraYaw;
        frameCameraPitch = cameraPitch;
        return lights;
    }

    /**
     * Raises an entity's block light to the strongest unoccluded flashlight reaching it (never its own holder's).
     * 将实体方块光提升到照到它的最强未遮挡手电筒亮度（不含持有者自己的手电）。
     */
    public static int boostBlockLight(Entity entity, float tickDelta, int blockLight) {
        if (TRACKED.isEmpty() || blockLight >= 15) {
            return blockLight;
        }
        // Bounding-box centre at the frame-interpolated position. / 逐帧插值位置处的碰撞箱中心。
        Box box = entity.getBoundingBox();
        double x = (box.minX + box.maxX) * 0.5 + MathHelper.lerp(tickDelta, entity.prevX, entity.getX()) - entity.getX();
        double y = (box.minY + box.maxY) * 0.5 + MathHelper.lerp(tickDelta, entity.prevY, entity.getY()) - entity.getY();
        double z = (box.minZ + box.maxZ) * 0.5 + MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ()) - entity.getZ();
        return boost(collect(MinecraftClient.getInstance(), tickDelta), entity.getId(), x, y, z, 0.0, blockLight);
    }

    /**
     * Block-entity variant of {@link #boostBlockLight(Entity, float, int)}, sampled at the block centre.
     * {@link #boostBlockLight(Entity, float, int)} 的方块实体版本，在方块中心采样。
     */
    public static int boostBlockLightAt(BlockPos pos, float tickDelta, int blockLight) {
        if (TRACKED.isEmpty() || blockLight >= 15) {
            return blockLight;
        }
        return boost(collect(MinecraftClient.getInstance(), tickDelta), NO_OWNER,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, BLOCK_ENTITY_SAMPLE_PULL, blockLight);
    }

    private static int boost(List<FlashlightLight> lights, int ownerToSkip,
                             double x, double y, double z, double samplePull, int blockLight) {
        int best = blockLight;
        for (int i = 0, size = lights.size(); i < size; i++) {
            FlashlightLight light = lights.get(i);
            FlashlightRayMap rayMap = light.rayMap();
            if (rayMap == null || light.ownerEntityId() == ownerToSkip) {
                continue;
            }
            Vec3d origin = light.origin();
            double lx = x - origin.x;
            double ly = y - origin.y;
            double lz = z - origin.z;
            double distanceSq = lx * lx + ly * ly + lz * lz;
            if (distanceSq >= RANGE_SQ || distanceSq < 1.0e-8) {
                continue;
            }
            double distance = Math.sqrt(distanceSq);
            Vec3d direction = light.direction();
            double cosAngle = (lx * direction.x + ly * direction.y + lz * direction.z) / distance;
            if (cosAngle <= COS_SPILL) {
                continue;
            }
            int level = FlashlightBeamRules.entityBlockLight(FlashlightBeamRules.intensity(cosAngle, distance));
            if (level <= best) {
                continue;
            }
            double pull = Math.min(samplePull, distance) / distance;
            // Even camera-anchored lights test occlusion here: the camera may not see this object (e.g. through walls).
            // 即使是相机锚定光源也在此检测遮挡：相机未必看得到该物体（例如隔墙）。
            if (!rayMap.isLit(x - lx * pull, y - ly * pull, z - lz * pull)) {
                continue;
            }
            best = level;
            if (best >= 15) {
                break;
            }
        }
        return best;
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != trackedWorld) {
            clear();
            trackedWorld = world;
        }
        if (world == null) {
            return;
        }
        tickCount++;
        int count = selectNearest(client, world);

        for (TrackedFlashlight tracked : TRACKED) {
            tracked.seen = false;
        }
        Entity cameraEntity = client.getCameraEntity();
        boolean firstPerson = client.options.getPerspective().isFirstPerson();
        for (int i = 0; i < count; i++) {
            AbstractClientPlayerEntity player = NEAREST[i];
            NEAREST[i] = null;
            TrackedFlashlight tracked = find(player);
            if (tracked == null) {
                tracked = new TrackedFlashlight(player);
                TRACKED.add(tracked);
            }
            tracked.seen = true;
            tracked.tick(CASTER, world, firstPerson && player == cameraEntity, tickCount);
        }
        for (int i = TRACKED.size() - 1; i >= 0; i--) {
            TrackedFlashlight tracked = TRACKED.get(i);
            if (!tracked.seen) {
                tracked.close();
                TRACKED.remove(i);
            }
        }
    }

    /** Fills NEAREST with the closest lit-flashlight holders to the camera. / 选出离相机最近的已开启手电持有者。 */
    private static int selectNearest(MinecraftClient client, ClientWorld world) {
        Vec3d reference = referencePosition(client);
        int count = 0;
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            if (player.isRemoved() || player.isSpectator() || !FlashlightItem.isHeldOn(player)) {
                continue;
            }
            double distanceSq = player.squaredDistanceTo(reference);
            if (count == MAX_LIGHTS && distanceSq >= NEAREST_DISTANCE_SQ[MAX_LIGHTS - 1]) {
                continue;
            }
            int slot = count < MAX_LIGHTS ? count++ : MAX_LIGHTS - 1;
            while (slot > 0 && NEAREST_DISTANCE_SQ[slot - 1] > distanceSq) {
                NEAREST[slot] = NEAREST[slot - 1];
                NEAREST_DISTANCE_SQ[slot] = NEAREST_DISTANCE_SQ[slot - 1];
                slot--;
            }
            NEAREST[slot] = player;
            NEAREST_DISTANCE_SQ[slot] = distanceSq;
        }
        return count;
    }

    private static Vec3d referencePosition(MinecraftClient client) {
        Camera camera = client.gameRenderer.getCamera();
        if (camera.isReady()) {
            return camera.getPos();
        }
        return client.player != null ? client.player.getEyePos() : Vec3d.ZERO;
    }

    private static @Nullable TrackedFlashlight find(AbstractClientPlayerEntity player) {
        for (TrackedFlashlight tracked : TRACKED) {
            if (tracked.player == player) {
                return tracked;
            }
        }
        return null;
    }

    private static void clear() {
        for (TrackedFlashlight tracked : TRACKED) {
            tracked.close();
        }
        TRACKED.clear();
        Arrays.fill(NEAREST, null);
        trackedWorld = null;
        frameLights = List.of();
        frameTick = Long.MIN_VALUE;
        frameCameraPos = null;
    }
}
