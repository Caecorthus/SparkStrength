package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineTrainMotion;
import dev.doctor4t.wathe.client.particle.SnowflakeParticle;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe spawns snowflakes 20 blocks behind the player flying 2 blocks per tick past the window. As the train slows,
 * the part of that speed that comes from the train is taken away and the spawn point moves round the player, so a
 * stopped train sees snow drifting down instead of a frozen-speed blizzard. The player's own movement is kept, and the
 * speed never reaches exactly 0, which Wathe treats as "hit a wall".
 * Wathe 在玩家身后 20 格生成雪花，以每 tick 2 格的速度掠过窗外。列车减速时，去掉其中来自列车的那部分速度，并把生成点移到
 * 玩家周围，于是停下的列车看到的是缓缓飘落的雪，而不是速度不变的暴风雪。玩家自己的移动保留，速度也不会正好变成 0，因为
 * Wathe 把 0 当作「撞墙」。
 */
@Mixin(value = SnowflakeParticle.class, remap = false)
public abstract class EngineSnowflakeMixin extends SpriteBillboardParticle {
    private static final double TRAIN_DRIFT = 2.0;
    private static final double SPAWN_BEHIND = 20.0;
    private static final double SPREAD = 10.0;

    protected EngineSnowflakeMixin(ClientWorld world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sparkstrength$followEngineMotion(ClientWorld world, double x, double y, double z, double velocityX,
                                                  double velocityY, double velocityZ, SpriteProvider spriteProvider,
                                                  CallbackInfo ci) {
        double f = EngineTrainMotion.factor();
        if (f >= 0.999) {
            return;
        }
        double still = 1.0 - f;
        double drift = velocityX - TRAIN_DRIFT * still;
        this.velocityX = Math.abs(drift) < 1.0E-3 ? 1.0E-3 : drift;
        double shift = SPAWN_BEHIND * still + (world.random.nextDouble() * 2.0 - 1.0) * SPREAD * still;
        double newX = x + shift;
        // Wathe only checked the roof over the old spawn point. / Wathe 只检查过原出生点上方的车顶。
        if (y < world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(newX), (int) Math.floor(z))) {
            markDead();
            return;
        }
        setPos(newX, y, z);
        this.prevPosX = this.x;
    }
}
