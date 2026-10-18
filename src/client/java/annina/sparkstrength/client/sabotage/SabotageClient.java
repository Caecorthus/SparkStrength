package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.block.EngineUnitBlock;
import annina.sparkstrength.block.JunctionBoxBlock;
import annina.sparkstrength.client.compat.SparkWitchBlindViewCompat;
import annina.sparkstrength.network.sabotage.CancelChannelC2SPacket;
import annina.sparkstrength.network.sabotage.ChannelProgressS2CPacket;
import annina.sparkstrength.network.sabotage.CloseBreakerRepairS2CPacket;
import annina.sparkstrength.network.sabotage.OpenBreakerRepairS2CPacket;
import annina.sparkstrength.network.sabotage.OpenEngineRepairS2CPacket;
import annina.sparkstrength.network.sabotage.SabotageStatusS2CPacket;
import annina.sparkstrength.sabotage.EngineEconomy;
import annina.sparkstrength.sabotage.EngineHalt;
import annina.sparkstrength.sabotage.EnginePhase;
import annina.sparkstrength.sabotage.JunctionBoxService;
import annina.sparkstrength.sabotage.JunctionBoxState;
import annina.sparkstrength.sabotage.SabotageBlockScan;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Client wiring of the sabotage system: packets, the loaded-chunk scan for junction boxes and breakers, sparks from
 * opened boxes, the sky-light block during an outage, and cancelling the prying/hacking bar the moment the button is
 * released or the aim leaves the box.
 * 破坏系统的客户端接线：数据包、在已加载区块中扫描接线箱与电闸、打开的箱子冒火花、熄灯期间屏蔽天空光照，
 * 以及在松开按键或准星离开箱子的瞬间取消撬开/入侵进度条。
 */
public final class SabotageClient {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final double ADMIN_SCAN_RADIUS = 48.0;
    private static final double SPARK_RANGE_SQ = 32.0 * 32.0;
    private static final DustParticleEffect RED_LED = new DustParticleEffect(new Vector3f(1.0F, 0.15F, 0.12F), 0.6F);

    private SabotageClient() {
    }

    public static void register() {
        // Client init runs on the render thread: only this thread follows the local player's shop discount.
        // 客户端初始化在渲染线程上执行：只有这个线程跟随本地玩家的商店折扣。
        EngineEconomy.registerClientThread(Thread.currentThread());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(),
                SparkStrengthBlocks.junctionBox(), SparkStrengthBlocks.circuitBreaker(), SparkStrengthBlocks.engineUnit());
        ClientPlayNetworking.registerGlobalReceiver(SabotageStatusS2CPacket.ID, (payload, context) -> {
            SabotageClientState.setStatus(payload);
            EngineHalt.setClientPhase(EnginePhase.byWire(payload.enginePhaseWire()));
            boolean bonusChanged = EngineEconomy.clientBonus() != payload.engineBonus();
            EngineEconomy.setClientBonus(payload.engineBonus());
            if (bonusChanged) {
                EngineShopDisplay.refreshOpenShop(context.client());
            }
            EngineTrainMotion.onStatus();
        });
        ClientPlayNetworking.registerGlobalReceiver(ChannelProgressS2CPacket.ID, (payload, context) -> {
            ClientWorld world = context.client().world;
            SabotageClientState.onProgress(payload, world == null ? 0L : world.getTime());
        });
        ClientPlayNetworking.registerGlobalReceiver(OpenBreakerRepairS2CPacket.ID,
                (payload, context) -> context.client().setScreen(new BreakerRepairScreen(payload)));
        ClientPlayNetworking.registerGlobalReceiver(OpenEngineRepairS2CPacket.ID,
                (payload, context) -> context.client().setScreen(new EngineRepairScreen(payload)));
        // One close packet for both panels; session ids never repeat between them. / 两种面板共用一个关闭包；会话 id 不会重复。
        ClientPlayNetworking.registerGlobalReceiver(CloseBreakerRepairS2CPacket.ID, (payload, context) -> {
            if (context.client().currentScreen instanceof BreakerRepairScreen screen) {
                screen.onServerClose(payload.sessionId(), payload.reason());
            } else if (context.client().currentScreen instanceof EngineRepairScreen screen) {
                screen.onServerClose(payload.sessionId(), payload.reason());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(SabotageClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(EngineTrainMotion::tick);
        HudRenderCallback.EVENT.register(SabotageHud::render);
        SabotageOutlineRenderer.register();
        // SparkWitch's Blind echo view paints over LAST: hand it the public outlines too (fails closed without it).
        // SparkWitch 盲人的回声画面会盖住 LAST 阶段：把公开描边也交给它（缺失时安全失效）。
        SparkWitchBlindViewCompat.addSeeThroughOutlines(SabotageOutlineRenderer::publicOutlines);
        // JOIN runs on the client thread before any play packet is handled, so the first status cannot be wiped.
        // JOIN 在客户端线程上、任何游戏数据包处理之前执行，所以不会抹掉第一个状态包。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            EngineHalt.setClientPhase(null);
            EngineEconomy.setClientBonus(false);
            EngineTrainMotion.onJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            SabotageClientState.reset();
            SabotageSkyLight.set(client, null);
            EngineHalt.setClientPhase(null);
            EngineEconomy.setClientBonus(false);
            EngineTrainMotion.reset();
        }));
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null) {
            return;
        }
        if (world.getTime() % SCAN_INTERVAL_TICKS == 0) {
            scan(client, world, player);
        }
        updateDarkness(client, world, player);
        watchChannel(client, world, player);
        sparks(world, player);
        engineParticles(world, player);
    }

    /** Lights sabotage darkness for living players; spectators and the dead keep their view. / 存活玩家看到灯光破坏的黑暗；观战者与死者保持原有视野。 */
    private static void updateDarkness(MinecraftClient client, ClientWorld world, ClientPlayerEntity player) {
        boolean dark = SabotageClientState.lightsOut() && !player.isSpectator()
                && GameFunctions.isPlayerPlayingAndAlive(player);
        Box area = dark ? MapVariablesWorldComponent.KEY.get(world).getPlayArea() : null;
        SabotageSkyLight.set(client, area);
    }

    private static void scan(MinecraftClient client, ClientWorld world, ClientPlayerEntity player) {
        boolean inRound = GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator();
        Box area;
        if (holdsConfigurator(player)) {
            area = player.getBoundingBox().expand(ADMIN_SCAN_RADIUS);
        } else if (inRound) {
            area = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        } else {
            SabotageClientState.setFixtures(List.of());
            return;
        }
        List<SabotageClientState.Fixture> found = new ArrayList<>();
        SabotageBlockScan.scan(world, area, (pos, state) -> found.add(new SabotageClientState.Fixture(pos, state)));
        SabotageClientState.setFixtures(found);
    }

    static boolean holdsConfigurator(ClientPlayerEntity player) {
        return player.getMainHandStack().isOf(SparkStrengthItems.circuitConfigurator())
                || player.getOffHandStack().isOf(SparkStrengthItems.circuitConfigurator());
    }

    /**
     * The server cannot tell a released button from the 4-tick resend gap, so the client reports it at once.
     * 服务端无法区分「松开按键」与原版 4 tick 的重发间隔，所以由客户端立刻报告。
     */
    private static void watchChannel(MinecraftClient client, ClientWorld world, ClientPlayerEntity player) {
        SabotageClientState.Channel channel = SabotageClientState.channel();
        if (channel == null) {
            return;
        }
        if (channel.finishedFlashTicks > 0) {
            if (--channel.finishedFlashTicks == 0) {
                SabotageClientState.clearChannel();
            }
            return;
        }
        if (channel.cancelSent) {
            return;
        }
        HitResult target = client.crosshairTarget;
        boolean aimed = target instanceof BlockHitResult hit && target.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(channel.pos);
        boolean holding = client.options.useKey.isPressed() && client.currentScreen == null && !player.isSneaking();
        if (!aimed || !holding) {
            channel.cancelSent = true;
            ClientPlayNetworking.send(new CancelChannelC2SPacket(channel.id));
            SabotageClientState.cancelChannel(channel);
        }
    }

    /**
     * Engine units, at a steady rate (randomDisplayTick is not called every tick): an intact unit now and then gives a
     * green sparkle, a damaged one smokes from its exhaust, a wrecked one pours black smoke and throws sparks.
     * 动力装置，按固定频率（randomDisplayTick 并不是每 tick 都会调用）：完好的偶尔冒一点绿色闪光，受损的从排气管冒烟，
     * 损毁的冒浓黑烟并迸出火花。
     */
    private static void engineParticles(ClientWorld world, ClientPlayerEntity player) {
        Random random = world.random;
        for (SabotageClientState.Fixture fixture : SabotageClientState.fixtures()) {
            BlockState state = world.getBlockState(fixture.pos());
            if (!state.isOf(SparkStrengthBlocks.engineUnit())
                    || player.squaredDistanceTo(Vec3d.ofCenter(fixture.pos())) > SPARK_RANGE_SQ) {
                continue;
            }
            Direction facing = state.get(EngineUnitBlock.FACING);
            Vec3d exhaust = EngineUnitLook.exhaust(fixture.pos(), facing);
            Vec3d front = EngineUnitLook.panel(fixture.pos(), facing);
            switch (state.get(EngineUnitBlock.CONDITION)) {
                case INTACT -> {
                    if (random.nextInt(EngineUnitLook.INTACT_SPARKLE_INTERVAL) == 0) {
                        world.addParticle(ParticleTypes.HAPPY_VILLAGER,
                                front.x + (random.nextDouble() - 0.5) * 0.6, front.y + random.nextDouble() * 0.4,
                                front.z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.02, 0.0);
                    }
                }
                case DAMAGED -> {
                    if (random.nextFloat() < 0.35F) {
                        world.addParticle(ParticleTypes.LARGE_SMOKE, exhaust.x, exhaust.y, exhaust.z,
                                (random.nextDouble() - 0.5) * 0.02, 0.05 + random.nextDouble() * 0.03,
                                (random.nextDouble() - 0.5) * 0.02);
                    }
                }
                case WRECKED -> {
                    for (int i = 0; i < 2; i++) {
                        if (random.nextFloat() < 0.6F) {
                            world.addParticle(ParticleTypes.LARGE_SMOKE,
                                    exhaust.x + (random.nextDouble() - 0.5) * 0.3, exhaust.y,
                                    exhaust.z + (random.nextDouble() - 0.5) * 0.3,
                                    (random.nextDouble() - 0.5) * 0.03, 0.07 + random.nextDouble() * 0.05,
                                    (random.nextDouble() - 0.5) * 0.03);
                        }
                    }
                    if (random.nextFloat() < 0.12F) {
                        int count = 2 + random.nextInt(4);
                        for (int i = 0; i < count; i++) {
                            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                                    front.x + (random.nextDouble() - 0.5) * 0.5, front.y + (random.nextDouble() - 0.5) * 0.5,
                                    front.z + (random.nextDouble() - 0.5) * 0.5,
                                    (random.nextDouble() - 0.5) * 0.3 - facing.getOffsetX() * 0.1,
                                    random.nextDouble() * 0.15,
                                    (random.nextDouble() - 0.5) * 0.3 - facing.getOffsetZ() * 0.1);
                        }
                    }
                }
            }
        }
    }

    /** Opened boxes spark and smoke; hacked ones also blink their red light. / 打开的箱子冒火花与烟，已入侵的还会闪红灯。 */
    private static void sparks(ClientWorld world, ClientPlayerEntity player) {
        Random random = world.random;
        for (SabotageClientState.Fixture fixture : SabotageClientState.fixtures()) {
            BlockState state = world.getBlockState(fixture.pos());
            if (!state.isOf(SparkStrengthBlocks.junctionBox())) {
                continue;
            }
            JunctionBoxState look = state.get(JunctionBoxBlock.STATE);
            if (!look.isOpen() || player.squaredDistanceTo(Vec3d.ofCenter(fixture.pos())) > SPARK_RANGE_SQ) {
                continue;
            }
            Direction facing = state.get(JunctionBoxBlock.FACING);
            Vec3d front = JunctionBoxService.frontOf(fixture.pos(), facing);
            if (random.nextFloat() < 0.22F) {
                int count = 1 + random.nextInt(4);
                for (int i = 0; i < count; i++) {
                    world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            front.x + (random.nextDouble() - 0.5) * 0.4,
                            front.y + (random.nextDouble() - 0.5) * 0.4,
                            front.z + (random.nextDouble() - 0.5) * 0.4,
                            (random.nextDouble() - 0.5) * 0.3 - facing.getOffsetX() * 0.1,
                            random.nextDouble() * 0.15,
                            (random.nextDouble() - 0.5) * 0.3 - facing.getOffsetZ() * 0.1);
                }
            }
            if (random.nextFloat() < 0.04F) {
                world.addParticle(ParticleTypes.SMOKE, front.x, front.y + 0.1, front.z, 0.0, 0.02, 0.0);
            }
            if (random.nextFloat() < 0.012F) {
                world.playSound(front.x, front.y, front.z, SparkStrengthSounds.JUNCTION_BOX_SPARK, SoundCategory.BLOCKS,
                        0.35F, 0.9F + random.nextFloat() * 0.3F, false);
            }
            if (look == JunctionBoxState.HACKED && world.getTime() % 20 < 10 && random.nextFloat() < 0.5F) {
                world.addParticle(RED_LED, front.x, front.y, front.z, 0.0, 0.0, 0.0);
            }
        }
    }
}
