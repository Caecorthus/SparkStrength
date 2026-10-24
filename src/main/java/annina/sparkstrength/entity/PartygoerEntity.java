package annina.sparkstrength.entity;

import annina.sparkstrength.role.partyanimal.PartygoerBrain;
import annina.sparkstrength.role.partyanimal.PartygoerService;
import annina.sparkstrength.role.partyanimal.PartygoerState;
import net.minecraft.block.LadderBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Partygoer (派对客): the Party Animal's summoned zombie. All behaviour lives in the server-only
 * {@link PartygoerBrain}; this class is plumbing (no vanilla AI, no gravity, no collisions, immune to vanilla damage).
 * 派对客：派对狂召唤的僵尸。所有行为都在仅服务端的 PartygoerBrain 中；本类只是管线（无原版 AI、无重力、无碰撞、免疫原版伤害）。
 *
 * <p>Movement is kinematic along a path the brain plans on its own train grid, so the entity is noClip and moved with
 * {@link #setPosition}. It still has a hitbox for weapon hits, which go through {@link PartygoerService#killByWeapon}.
 * 移动是沿大脑在自有列车网格上规划的路径做运动学位移，因此实体为 noClip 并用 setPosition 移动。它仍保留命中箱供武器命中，
 * 命中统一走 PartygoerService#killByWeapon。</p>
 *
 * <p>Privacy: the owner is never in the DataTracker. The server brain holds it, and {@code PartygoerOwnerComponent}
 * sends it only to killer-side and spectating clients (for the pink outline and the blackout check).
 * 隐私：召唤者绝不写入 DataTracker。它保存在服务端大脑中，{@code PartygoerOwnerComponent} 只把它发给杀手一方与旁观的客户端
 * （用于粉色透视与停电判定）。</p>
 */
public class PartygoerEntity extends PathAwareEntity {
    private static final TrackedData<Byte> STATE =
            DataTracker.registerData(PartygoerEntity.class, TrackedDataHandlerRegistry.BYTE);

    /**
     * Where the carried player rides: standing in front of the chest, inside the outstretched arms, feet on the floor
     * (a player rides {@code PlayerEntity.VEHICLE_ATTACHMENT_POS} = 0.6 above its feet).
     * 被抱玩家的位置：站在胸前、伸出的双臂之间，双脚着地（玩家的骑乘点在脚上方 0.6 格，即 VEHICLE_ATTACHMENT_POS）。
     */
    private static final Vec3d CARRY_ATTACHMENT = new Vec3d(0.0, 0.6, 0.5);
    /**
     * On a ladder it holds them closer, so their head stays in the ladder's shaft (a floor hole is one block wide).
     * 在梯子上抱得更紧，让被抱者的头留在梯子井里（地板洞口只有一格宽）。
     */
    private static final Vec3d LADDER_CARRY_ATTACHMENT = new Vec3d(0.0, 0.6, 0.25);

    private @Nullable PartygoerBrain brain;

    public PartygoerEntity(EntityType<? extends PartygoerEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setPersistent();
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STATE, (byte) PartygoerState.HUNT.wire());
    }

    public PartygoerState partyState() {
        return PartygoerState.fromWire(dataTracker.get(STATE));
    }

    public void setPartyState(PartygoerState state) {
        dataTracker.set(STATE, (byte) state.wire());
    }

    /** Server only; null on clients and for a stale entity loaded from disk. / 仅服务端；客户端与从存档读出的过期实体为 null。 */
    public @Nullable PartygoerBrain brain() {
        return brain;
    }

    public void attachBrain(PartygoerBrain brain) {
        this.brain = brain;
    }

    @Override
    protected void initGoals() {
        // Driven by PartygoerBrain. / 由 PartygoerBrain 驱动。
    }

    @Override
    public void tick() {
        this.noClip = true;
        this.setVelocity(Vec3d.ZERO);
        super.tick();
        if (getWorld() instanceof ServerWorld) {
            if (brain == null) {
                // Never persists across a restart or a round. / 不跨重启或对局存在。
                discard();
                return;
            }
            brain.tick();
        }
    }

    // ---- Combat: only PartygoerService may end it / 战斗：只有 PartygoerService 能结束它 ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL) && getWorld() instanceof ServerWorld) {
            // /kill: leave quietly, no corpse or reward. / /kill：安静离场，无尸体无奖励。
            PartygoerService.vanishQuietly(this);
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.isOf(DamageTypes.GENERIC_KILL);
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void pushAwayFrom(Entity entity) {
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    // ---- Carrying / 扛人 ----

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof PlayerEntity && getPassengerList().isEmpty();
    }

    /**
     * Turned with the body, as the vanilla attachments are turned with the yaw: the client draws the body, and the
     * rider's own body follows it.
     * 随身体朝向旋转（原版附着点随 yaw 旋转）：客户端按身体朝向绘制，被抱者的身体也跟随它。
     */
    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        Vec3d attachment = onLadder() ? LADDER_CARRY_ATTACHMENT : CARRY_ATTACHMENT;
        return attachment.rotateY(-bodyYaw * MathHelper.RADIANS_PER_DEGREE);
    }

    /**
     * Feet in a ladder block or on top of one; read from the world, so client and server agree.
     * 脚在梯子方块里或梯子顶端；从世界读取，客户端与服务端一致。
     */
    private boolean onLadder() {
        BlockPos feet = getBlockPos();
        return getWorld().getBlockState(feet).getBlock() instanceof LadderBlock
                || getWorld().getBlockState(feet.down()).getBlock() instanceof LadderBlock;
    }

    @Override
    public @Nullable net.minecraft.entity.LivingEntity getControllingPassenger() {
        return null;
    }

    // ---- Quiet by default; the brain plays every sound / 默认无声，所有声音由大脑播放 ----

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean shouldRenderName() {
        return false;
    }

    /** Inside a Taotie it rides along hidden at its feet: never the target of a crosshair or a shot. / 在饕餮肚里时隐身跟在其脚下：不会成为准星或射击的目标。 */
    @Override
    public boolean canHit() {
        return partyState() != PartygoerState.SWALLOWED && super.canHit();
    }
}
