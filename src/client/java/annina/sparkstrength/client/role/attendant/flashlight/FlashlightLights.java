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
     * Holders farther than this from the camera are ignored. A beam only lights what lies within RANGE_BLOCKS of its
     * origin, so everything within 48 blocks of the camera keeps every light that can reach it; beyond that a lit
     * spot spans a few pixels, and tracking it would cost a ray grid per tick for nothing visible.
     * 距相机超过此距离的持有者被忽略。光束只照亮距其原点 RANGE_BLOCKS 以内的物体，因此相机 48 格内的一切都保有所有能照到它的
     * 光源；更远处的光斑只占几个像素，追踪它们每 tick 都要投射一张网格却看不出差别。
     */
    static final double TRACK_DISTANCE = FlashlightBeamRules.RANGE_BLOCKS + 48.0;
    private static final double TRACK_DISTANCE_SQ = TRACK_DISTANCE * TRACK_DISTANCE;
    private static final int NO_OWNER = Integer.MIN_VALUE;
    private static final double RANGE_SQ = FlashlightBeamRules.RANGE_BLOCKS * FlashlightBeamRules.RANGE_BLOCKS;
    private static final double COS_SPILL = FlashlightBeamRules.cosSpill();

    private static final List<TrackedFlashlight> TRACKED = new ArrayList<>(MAX_LIGHTS);
    private static final FlashlightRayCaster CASTER = new FlashlightRayCaster();
    private static final AbstractClientPlayerEntity[] NEAREST = new AbstractClientPlayerEntity[MAX_LIGHTS];
    private static final double[] NEAREST_DISTANCE_SQ = new double[MAX_LIGHTS];
    private static final TrackedFlashlight[] WANTS_RECAST = new TrackedFlashlight[MAX_LIGHTS];
    /**
     * Ray-grid recasts allowed per tick (about 0.3-0.5 ms each). Bounds the worst case when every tracked holder
     * moves at once; a waiting light recasts the next tick.
     * 每 tick 允许的射线网格重投射次数（每次约 0.3-0.5 毫秒）。限制所有被追踪持有者同时移动时的最坏情况；等待的光源下一 tick 重投射。
     */
    static final int RECASTS_PER_TICK = 2;
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
     * Raises an entity's block light to the strongest unoccluded flashlight reaching it. A holder is never lit by its
     * own beam but gets {@link FlashlightBeamRules#HOLDER_BLOCK_LIGHT} from the reflector spill.
     * 将实体方块光提升到照到它的最强未遮挡手电筒亮度。持有者不会被自己的光束照亮，但会获得反光杯溢光的下限亮度。
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
        return boost(MinecraftClient.getInstance(), tickDelta, entity.getId(), entity, null, x, y, z, blockLight);
    }

    /**
     * Block-entity variant of {@link #boostBlockLight(Entity, float, int)}: brightness from the block centre,
     * occlusion sampled on the viewer's side of the block (its renderer shares one light across all faces).
     * {@link #boostBlockLight(Entity, float, int)} 的方块实体版本：亮度按方块中心计算，遮挡在方块朝向观看者的一侧采样
     * （其渲染器所有面共用一个光照）。
     */
    public static int boostBlockLightAt(BlockPos pos, float tickDelta, int blockLight) {
        if (TRACKED.isEmpty() || blockLight >= 15) {
            return blockLight;
        }
        return boost(MinecraftClient.getInstance(), tickDelta, NO_OWNER, null, pos,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, blockLight);
    }

    /** Client thread: a block changed, so beams that can see it recast next tick. / 方块变化，可能看到它的光束下一 tick 重投射。 */
    public static void onBlockChanged(BlockPos pos) {
        for (TrackedFlashlight tracked : TRACKED) {
            tracked.onBlockChanged(pos);
        }
    }

    /**
     * Brightness comes from the frame-interpolated beam; occlusion is an exact segment test from the beam's tick
     * origin, cached per target and light for the tick (at most a few short casts per tick).
     * 亮度来自逐帧插值的光束；遮挡为从光束 tick 原点出发的精确线段检测，按目标与光源在本 tick 内缓存
     * （每 tick 至多几次短距离投射）。
     */
    private static int boost(MinecraftClient client, float tickDelta, int holderId, @Nullable Entity entity,
                             @Nullable BlockPos blockPos, double x, double y, double z, int blockLight) {
        ClientWorld world = client.world;
        if (world == null) {
            return blockLight;
        }
        List<FlashlightLight> lights = collect(client, tickDelta);
        int best = blockLight;
        for (int i = 0, size = lights.size(); i < size; i++) {
            FlashlightLight light = lights.get(i);
            if (light.ownerEntityId() == holderId) {
                best = Math.max(best, FlashlightBeamRules.HOLDER_BLOCK_LIGHT);
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
            TrackedFlashlight tracked = find(light.ownerEntityId());
            if (tracked == null) {
                continue;
            }
            // Even camera-anchored lights test occlusion here: the camera may not see this object (e.g. through walls).
            // 即使是相机锚定光源也在此检测遮挡：相机未必看得到该物体（例如隔墙）。
            boolean reaches = entity != null
                    ? tracked.reachesEntity(CASTER, world, entity, tickCount)
                    : tracked.reachesBlockEntity(CASTER, world, blockPos, client.gameRenderer.getCamera().getPos(),
                            tickCount);
            if (!reaches) {
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
        int wanting = 0;
        for (int i = 0; i < count; i++) {
            AbstractClientPlayerEntity player = NEAREST[i];
            NEAREST[i] = null;
            TrackedFlashlight tracked = find(player);
            if (tracked == null) {
                tracked = new TrackedFlashlight(player);
                TRACKED.add(tracked);
            }
            tracked.seen = true;
            if (tracked.updatePose(CASTER, world, firstPerson && player == cameraEntity, tickCount,
                    NEAREST_DISTANCE_SQ[i])) {
                WANTS_RECAST[wanting++] = tracked;
            }
        }
        // Stalest maps first, at most RECASTS_PER_TICK grids per tick; the rest wait a tick (their maps keep their
        // own basis, so shadows stay put in the world meanwhile).
        // 最久未更新的优先，每 tick 至多 RECASTS_PER_TICK 张网格；其余等待一 tick（射线图保留自身基向量，阴影在世界中不动）。
        for (int recasts = 0; recasts < RECASTS_PER_TICK && wanting > 0; recasts++) {
            int stalest = 0;
            for (int j = 1; j < wanting; j++) {
                if (WANTS_RECAST[j].castTick() < WANTS_RECAST[stalest].castTick()) {
                    stalest = j;
                }
            }
            WANTS_RECAST[stalest].recast(CASTER, world, tickCount);
            WANTS_RECAST[stalest] = WANTS_RECAST[--wanting];
            WANTS_RECAST[wanting] = null;
        }
        Arrays.fill(WANTS_RECAST, null);
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
            if (distanceSq > TRACK_DISTANCE_SQ
                    || (count == MAX_LIGHTS && distanceSq >= NEAREST_DISTANCE_SQ[MAX_LIGHTS - 1])) {
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

    private static @Nullable TrackedFlashlight find(int ownerEntityId) {
        for (TrackedFlashlight tracked : TRACKED) {
            if (tracked.player.getId() == ownerEntityId) {
                return tracked;
            }
        }
        return null;
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
