package annina.sparkstrength.client.role.spiritualist;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.client.compat.SparkWitchSecondaryKeyCompat;
import annina.sparkstrength.client.compat.SparkWitchWraithViewerCompat;
import annina.sparkstrength.client.mixin.spiritualist.SpiritPossessionCameraAccessor;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.spiritualist.SpiritPossessionPlayerComponent;
import annina.sparkstrength.network.spiritualist.SpiritPossessC2SPacket;
import annina.sparkstrength.network.spiritualist.SpiritPossessExitC2SPacket;
import annina.sparkstrength.network.spiritualist.SpiritPossessionStateS2CPacket;
import annina.sparkstrength.role.spiritualist.SpiritPossessionEndReason;
import annina.sparkstrength.role.spiritualist.SpiritPossessionRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.block.ShapeContext;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.spiritualist.SpiritCamera;
import org.agmas.noellesroles.client.spiritualist.SpiritCameraHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the Spiritualist's Wraith possession (灵界行者附身冤魂): the role skill 2 key, aiming, the camera on
 * the Wraith, the sneak exit and the body hold.
 * 灵界行者附身冤魂的客户端：职业技能 2 按键、瞄准、锁定在冤魂上的镜头、潜行退出与肉身保持。
 *
 * <p>The server runs every possession inside a NoellesRoles spirit projection, so NoellesRoles' spirit client already
 * provides the gray anonymous view, the HUD player, the drawn body and the blocked interactions; this class only moves
 * the camera from the spirit onto the Wraith (re-found by id every tick: a re-tracked Wraith is a new instance), keeps
 * the spirit and the mouse still, and freezes the body while the re-centred chunk view has dropped its chunks (1.21.1's
 * {@code ClientWorld.isChunkLoaded} is always true, so vanilla would let it fall).
 * 服务端让每次附身都运行在 NoellesRoles 的灵魂出窍之中，因此灰暗且认不出人的画面、HUD 玩家、肉身渲染与交互封锁都已由
 * NoellesRoles 的灵魂客户端提供；本类只把镜头从灵魂移到冤魂上（每刻按 id 重新查找：重新追踪的冤魂是新实例），让灵魂与鼠标
 * 保持不动，并在重新居中的区块视野卸载了肉身区块时冻结肉身（1.21.1 的 ClientWorld.isChunkLoaded 恒为 true，原版会让它下落）。</p>
 */
public final class SpiritPossessionClient {
    /** Cap on the post-possession body hold while its chunks come back. / 附身结束后等待肉身区块回来的保持上限。 */
    private static final int BODY_SETTLE_MAX_TICKS = 200;
    private static final int FEEDBACK_COLOR = 0xA064DC;
    /** Beyond this the possessed Wraith is snapped to the server's position rather than interpolated. / 超过此距离直接对齐服务端位置而非插值。 */
    private static final double SNAP_DISTANCE_SQUARED = 8.0D * 8.0D;

    // Session (render thread only). / 会话（仅渲染线程）。
    private static int targetId = -1;
    private static int remainingTicks;
    private static int missingTicks;
    private static int exitWaitTicks;
    private static boolean sneakWasDown;
    /** The possession started its own projection, so its spirit is only a placeholder. / 附身自行开启了出窍，其灵魂只是占位。 */
    private static boolean fromBody;
    private static @Nullable ClientPlayerEntity sessionPlayer;
    private static @Nullable ClientWorld sessionWorld;
    // Post-possession body hold. / 附身结束后的肉身保持。
    private static @Nullable ClientPlayerEntity settleBody;
    private static int settleTicks;
    // Aim, refreshed every tick for the HUD and the key. / 瞄准结果，每刻刷新，供 HUD 与按键使用。
    private static @Nullable PlayerEntity aimedWraith;
    private static boolean keyAvailable;
    private static boolean registered;

    private SpiritPossessionClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(SpiritPossessionStateS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> onState(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(SpiritPossessionClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        if (!FabricLoader.getInstance().isModLoaded("sparkwitch")) {
            // Wraiths only exist with SparkWitch. / 冤魂只在安装 SparkWitch 时存在。
            return;
        }
        SparkWitchWraithViewerCompat.addViewerGate((viewer, wraith) -> seesWraiths(viewer));
        // SparkWitch dispatches by the raw Wathe role id. / SparkWitch 按原始 Wathe 职业 id 分发。
        keyAvailable = SparkWitchSecondaryKeyCompat.register(SpiritPossessionRules.SPIRITUALIST_ID,
                SpiritPossessionClient::onKeyPressed);
        if (!keyAvailable) {
            SparkStrength.LOGGER.warn("Spiritualist possession has no key: SparkWitch Role Skill 2 hook unavailable or taken.");
        }
    }

    // ------------------------------------------------------------------------------------------------ queries

    /** A possession is running on this client. / 本客户端正在附身。 */
    public static boolean isPossessing() {
        return targetId >= 0;
    }

    /** Role skill 2 reaches the possession. / 职业技能 2 能触发附身。 */
    public static boolean isKeyAvailable() {
        return keyAvailable;
    }

    public static int remainingTicks() {
        return remainingTicks;
    }

    public static @Nullable PlayerEntity aimedWraith() {
        return aimedWraith;
    }

    /**
     * SparkWitch viewer gate (render thread): a living Spiritualist on a SparkStrength server sees every active Wraith's
     * body, in its body, its spirit and while possessing.
     * SparkWitch 观察者闸门（渲染线程）：SparkStrength 服务器上存活的灵界行者在肉身、灵魂与附身状态下都能看到所有活跃冤魂的身体。
     */
    public static boolean seesWraiths(@Nullable PlayerEntity viewer) {
        MinecraftClient client = MinecraftClient.getInstance();
        return viewer != null
                && viewer == client.player
                && isLocalSpiritualist(client.player)
                && ClientPlayNetworking.canSend(SpiritPossessC2SPacket.ID);
    }

    /** At least one active Wraith is known to this client (the HUD stays quiet otherwise). / 本客户端已知至少一个活跃冤魂（否则 HUD 不显示）。 */
    public static boolean anyWraithKnown(MinecraftClient client) {
        if (client.world == null) {
            return false;
        }
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player != client.player && !(player instanceof SpiritCamera) && SparkWitchCompat.isWraithActive(player)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLocalSpiritualist(@Nullable ClientPlayerEntity player) {
        return player != null
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.SPIRIT_WALKER);
    }

    /**
     * The body's own movement is frozen for the whole possession and afterwards until the chunks under it are back.
     * Server teleports still apply (they set the position).
     * 整个附身期间冻结肉身自身的移动，结束后保持到肉身下方区块重新加载为止。服务器传送仍然生效（直接设置位置）。
     */
    public static boolean freezesBody(ClientPlayerEntity player) {
        if (player != MinecraftClient.getInstance().player) {
            return false;
        }
        return targetId >= 0 || (settleTicks > 0 && player == settleBody && !bodyChunksLoaded(player));
    }

    // ------------------------------------------------------------------------------------------------ input

    /** Role skill 2: possess the aimed Wraith, or leave. / 职业技能 2：附身所瞄准的冤魂，或退出附身。 */
    private static void onKeyPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (isPossessing()) {
            requestExit();
            return;
        }
        if (!isLocalSpiritualist(player) || !ClientPlayNetworking.canSend(SpiritPossessC2SPacket.ID)) {
            return;
        }
        int cooldown = SpiritPossessionPlayerComponent.KEY.get(player).getCooldownTicks();
        if (cooldown > 0) {
            feedback(client, Text.translatable("hud.sparkstrength.spirit_possession.cooldown",
                    SpiritPossessionRules.displaySeconds(cooldown)));
            return;
        }
        PlayerEntity target = findAimedWraith(client);
        if (target == null) {
            feedback(client, Text.translatable("message.sparkstrength.spirit_possession.no_target"));
            return;
        }
        ClientPlayNetworking.send(new SpiritPossessC2SPacket(target.getId()));
    }

    /**
     * Render-grid centre axis for {@code WorldRenderer.setupTerrain} (vanilla renderer only): the camera while
     * possessing, otherwise vanilla's player position. Axis 0/1/2 = x/y/z.
     * WorldRenderer.setupTerrain 的渲染网格中心坐标（仅原版渲染器）：附身时取镜头位置，否则保持原版的玩家位置。axis 0/1/2 = x/y/z。
     */
    public static double renderGridCenter(double original, Camera camera, int axis) {
        if (targetId < 0) {
            return original;
        }
        Vec3d pos = camera.getPos();
        return axis == 0 ? pos.x : axis == 1 ? pos.y : pos.z;
    }

    /** Mouse look is dropped while possessing: the view is the Wraith's eyes. / 附身时丢弃鼠标转向：画面就是冤魂的视线。 */
    public static boolean blocksLook() {
        return targetId >= 0;
    }

    private static void requestExit() {
        if (exitWaitTicks > 0) {
            return;
        }
        exitWaitTicks = SpiritPossessionRules.EXIT_CONFIRM_TICKS;
        if (ClientPlayNetworking.canSend(SpiritPossessExitC2SPacket.ID)) {
            ClientPlayNetworking.send(new SpiritPossessExitC2SPacket());
        }
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    private static void onState(MinecraftClient client, SpiritPossessionStateS2CPacket payload) {
        if (payload.active()) {
            begin(client, payload.targetEntityId(), payload.durationTicks());
        } else {
            end(client, SpiritPossessionEndReason.fromWire(payload.reason()), true);
        }
    }

    private static void begin(MinecraftClient client, int entityId, int durationTicks) {
        if (client.player == null || client.world == null) {
            return;
        }
        targetId = entityId;
        remainingTicks = Math.max(0, durationTicks);
        missingTicks = 0;
        exitWaitTicks = 0;
        sessionPlayer = client.player;
        sessionWorld = client.world;
        settleBody = null;
        settleTicks = 0;
        aimedWraith = null;
        // NoellesRoles enables a projection on its next tick, so an inactive spirit view means the body.
        // NoellesRoles 在下一刻才开启出窍，因此灵魂画面未开启即表示从肉身发动。
        fromBody = !SpiritCameraHandler.isActive();
        // A sneak held at the start is not an exit. / 开始时已按住的潜行不算退出。
        sneakWasDown = client.options.sneakKey.isPressed();
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        holdView(client);
        bindCamera(client);
    }

    /**
     * End the local view. {@code restoreCamera} is false only when a vanilla camera packet is about to set the camera.
     * 结束本地画面。仅当原版镜头包即将自行设置镜头时 restoreCamera 为 false。
     */
    private static void end(MinecraftClient client, @Nullable SpiritPossessionEndReason reason, boolean restoreCamera) {
        if (targetId < 0) {
            return;
        }
        ClientPlayerEntity body = sessionPlayer;
        clearSession();
        // Keep the body still until its chunks are back (see freezesBody). / 肉身区块重新送达前保持静止（见 freezesBody）。
        settleBody = body;
        settleTicks = BODY_SETTLE_MAX_TICKS;
        if (restoreCamera) {
            restoreCamera(client);
        }
        // Sneak (or anything held while possessing) must not leak into the spirit or the body; a toggle-sneak latched by
        // the exit press would otherwise stay on. / 附身时按住的键（如潜行）不能漏给灵魂或肉身；退出时触发的切换式潜行也要解除。
        KeyBinding.unpressAll();
        KeyBinding.untoggleStickyKeys();
        String key = reason == null ? null : reason.translationKey();
        if (key != null) {
            client.inGameHud.setOverlayMessage(Text.translatable(key).withColor(FEEDBACK_COLOR), false);
        }
    }

    /**
     * A vanilla {@code SetCameraEntityS2CPacket} (spectating, a Taotie swallow...) outranks the possession: it ends
     * locally without restoring the camera and the server is told to stop.
     * 原版 SetCameraEntityS2CPacket（旁观、饕餮吞噬等）优先于附身：在本地结束且不恢复镜头，并通知服务器停止。
     */
    public static void onServerCameraOverride(MinecraftClient client) {
        if (targetId < 0) {
            return;
        }
        requestExit();
        end(client, null, false);
    }

    /** NoellesRoles just pointed the camera at a fresh spirit: look through the Wraith instead. / NoellesRoles 刚把镜头交给新灵魂：改为通过冤魂观看。 */
    public static void onSpiritCameraEnabled(MinecraftClient client) {
        if (targetId >= 0) {
            holdView(client);
            bindCamera(client);
        }
    }

    private static void reset() {
        clearSession();
        settleBody = null;
        settleTicks = 0;
        aimedWraith = null;
    }

    private static void clearSession() {
        targetId = -1;
        fromBody = false;
        remainingTicks = 0;
        missingTicks = 0;
        exitWaitTicks = 0;
        sessionPlayer = null;
        sessionWorld = null;
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (targetId < 0) {
            settle(client);
            aimedWraith = player != null && isLocalSpiritualist(player) && keyAvailable ? findAimedWraith(client) : null;
            return;
        }
        aimedWraith = null;
        if (player == null || player != sessionPlayer || client.world != sessionWorld) {
            // Respawn or world change: the old view is gone with its world. / 重生或换世界：旧画面随旧世界消失。
            reset();
            return;
        }
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        if (exitWaitTicks > 0 && --exitWaitTicks == 0) {
            // The server never confirmed: leave on our own. / 服务端始终未确认：自行退出。
            end(client, null, true);
            return;
        }
        if (bindCamera(client)) {
            missingTicks = 0;
        } else if (++missingTicks > SpiritPossessionRules.LINK_GRACE_TICKS) {
            requestExit();
            end(client, SpiritPossessionEndReason.TARGET_LOST, true);
            return;
        }
        holdView(client);
        boolean sneakDown = client.options.sneakKey.isPressed();
        if (SpiritPossessionRules.isFreshPress(sneakWasDown, sneakDown) && client.currentScreen == null) {
            requestExit();
        }
        sneakWasDown = sneakDown;
    }

    /**
     * First person only (third person would orbit the Wraith), and the spirit stays where it was so a possession that
     * began from the spirit resumes there.
     * 只用第一人称（第三人称会绕着冤魂旋转），灵魂停在原地，使从灵魂发动的附身结束后回到原处。
     */
    private static void holdView(MinecraftClient client) {
        if (!client.options.getPerspective().isFirstPerson()) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
        }
        SpiritCamera spirit = SpiritCameraHandler.getSpiritCamera();
        if (spirit != null) {
            if (spirit.input instanceof KeyboardInput) {
                spirit.input = new Input();
            }
            spirit.setVelocity(Vec3d.ZERO);
            liftPlaceholderSpirit(client, spirit);
        }
    }

    /**
     * NoellesRoles spawns the spirit inside the body; frozen there for a whole possession it blocks the body's standing
     * room on this client, which then draws the body crawling. A possession from the body never returns to that spirit
     * (the projection ends with it), so it is moved just above the body.
     * NoellesRoles 在肉身内生成灵魂；整个附身期间冻结在那里会占据本客户端上肉身的站立空间，使肉身显示为爬行。从肉身发动的附身
     * 结束时出窍随之结束、不会回到这个灵魂，因此把它移到肉身正上方。
     */
    private static void liftPlaceholderSpirit(MinecraftClient client, SpiritCamera spirit) {
        ClientPlayerEntity body = client.player;
        if (!fromBody || body == null || !spirit.getBoundingBox().intersects(body.getBoundingBox())) {
            return;
        }
        spirit.setPosition(spirit.getX(), body.getBoundingBox().maxY + 0.5D, spirit.getZ());
    }

    /**
     * Find the Wraith by id and look through it. Returns false while it is missing (the camera then keeps the last
     * frame until the grace period runs out).
     * 按 id 查找冤魂并通过它观看。缺失时返回 false（镜头保留最后画面，直到宽限期结束）。
     */
    private static boolean bindCamera(MinecraftClient client) {
        ClientWorld world = client.world;
        Entity found = world != null ? world.getEntityById(targetId) : null;
        if (!(found instanceof PlayerEntity wraith) || found == client.player || found.isRemoved()) {
            return false;
        }
        followServerPosition(wraith);
        if (client.getCameraEntity() != wraith) {
            swapCamera(client, wraith);
        }
        return true;
    }

    /**
     * While NoellesRoles' spirit view is up, the camera field is written directly so its grayscale shader survives the
     * swap (vanilla's setter drops post shaders, and NoellesRoles never reloads it mid-projection).
     * NoellesRoles 灵魂画面开启时直接写入镜头字段，使其灰度着色器在切换后保留（原版 setter 会丢弃后处理着色器，而 NoellesRoles
     * 在出窍中途不会重新加载）。
     */
    private static void swapCamera(MinecraftClient client, Entity camera) {
        if (SpiritCameraHandler.isActive()) {
            ((SpiritPossessionCameraAccessor) client).sparkstrength$setCameraEntityField(camera);
        } else {
            client.setCameraEntity(camera);
        }
    }

    /**
     * A Wraith teleported far away (a Jester shuffle, a Swapper swap...) is left behind in a chunk the re-centred view
     * has just unloaded on this client, where vanilla stops ticking entities, so it would never interpolate to where the
     * server says it is and the view would freeze over empty space. Snap it to the last position the server sent; its
     * new chunk starts it ticking again once it arrives. Ordinary walking stays interpolated.
     * 被传送到远处的冤魂（小丑洗牌、交换者交换等）会留在重新居中的视野刚在本客户端卸载的区块里，而原版不再更新这类实体，
     * 它永远不会插值到服务端所说的位置，画面会定格在空白处。这里直接对齐服务端最后发送的位置；新区块送达后它会重新开始更新。
     * 正常行走仍按插值移动。
     */
    private static void followServerPosition(PlayerEntity wraith) {
        Vec3d tracked = wraith.getTrackedPosition().getPos();
        if (wraith.getPos().squaredDistanceTo(tracked) > SNAP_DISTANCE_SQUARED) {
            wraith.refreshPositionAfterTeleport(tracked);
        }
    }

    /** Back to the spirit if NoellesRoles still projects, otherwise to the body. / NoellesRoles 仍在出窍时回到灵魂，否则回到肉身。 */
    private static void restoreCamera(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        Entity camera = client.getCameraEntity();
        if (camera != null && (camera == player || camera instanceof SpiritCamera)) {
            return;
        }
        SpiritCamera spirit = SpiritCameraHandler.isActive() ? SpiritCameraHandler.getSpiritCamera() : null;
        if (spirit != null) {
            spirit.input = new KeyboardInput(client.options);
            swapCamera(client, spirit);
        } else {
            swapCamera(client, player);
        }
    }

    /** Ends the post-possession body hold once its chunks are back, or at the cap. / 肉身区块回来后（或到达上限时）结束保持。 */
    private static void settle(MinecraftClient client) {
        if (settleTicks <= 0) {
            return;
        }
        ClientPlayerEntity body = client.player;
        if (body == null || body != settleBody || --settleTicks <= 0 || bodyChunksLoaded(body)) {
            settleBody = null;
            settleTicks = 0;
        }
    }

    // ------------------------------------------------------------------------------------------------ aim

    /**
     * The active Wraith under the crosshair within {@link SpiritPossessionRules#AIM_RANGE}, from the body's eyes or the
     * spirit's, or null. Walls and closed doors block the aim (outline shapes), as they block the eye.
     * 准星下 AIM_RANGE 以内的活跃冤魂（从肉身或灵魂的视点算起），没有则为 null。墙壁与关着的门会挡住瞄准（轮廓形状），与视线一致。
     */
    private static @Nullable PlayerEntity findAimedWraith(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        Entity camera = client.getCameraEntity();
        if (player == null || world == null || camera == null || (camera != player && !(camera instanceof SpiritCamera))) {
            return null;
        }
        Vec3d eye = camera.getCameraPosVec(1.0F);
        Vec3d look = camera.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(SpiritPossessionRules.AIM_RANGE));
        BlockHitResult blocks = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        double reachSquared = blocks.getType() == HitResult.Type.MISS
                ? SpiritPossessionRules.AIM_RANGE * SpiritPossessionRules.AIM_RANGE
                : eye.squaredDistanceTo(blocks.getPos());
        Box box = camera.getBoundingBox().stretch(look.multiply(SpiritPossessionRules.AIM_RANGE)).expand(1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(camera, eye, end, box,
                entity -> entity instanceof PlayerEntity candidate
                        && candidate != player
                        && !(candidate instanceof SpiritCamera)
                        && SparkWitchCompat.isWraithActive(candidate),
                reachSquared);
        return hit != null && hit.getEntity() instanceof PlayerEntity wraith ? wraith : null;
    }

    /** Every chunk the body's hitbox overlaps is loaded on this client. / 肉身碰撞箱覆盖的区块在本客户端均已加载。 */
    private static boolean bodyChunksLoaded(ClientPlayerEntity body) {
        Box box = body.getBoundingBox();
        ClientChunkManager chunks = body.clientWorld.getChunkManager();
        int minX = ChunkSectionPos.getSectionCoord(box.minX);
        int maxX = ChunkSectionPos.getSectionCoord(box.maxX);
        int minZ = ChunkSectionPos.getSectionCoord(box.minZ);
        int maxZ = ChunkSectionPos.getSectionCoord(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!chunks.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void feedback(MinecraftClient client, Text text) {
        client.inGameHud.setOverlayMessage(text.copy().withColor(FEEDBACK_COLOR), false);
    }
}
