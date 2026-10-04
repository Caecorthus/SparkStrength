package annina.sparkstrength;

import annina.sparkstrength.entity.AromaOrbEntity;
import annina.sparkstrength.entity.CapsuleEntity;
import annina.sparkstrength.entity.CaptureDeviceEntity;
import annina.sparkstrength.entity.CoolingOilEntity;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.entity.M67GrenadeEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class SparkStrengthEntities {
    public static final Identifier CAPSULE_ID = SparkStrength.id("capsule");
    public static final Identifier CAPTURE_DEVICE_ID = SparkStrength.id("capture_device");
    public static final Identifier M67_ID = SparkStrength.id("m67");
    public static final Identifier COOLING_OIL_ID = SparkStrength.id("cooling_oil");
    public static final Identifier AROMA_ORB_ID = SparkStrength.id("aroma_orb");
    public static final Identifier GRENADE_DRONE_ID = SparkStrength.id("grenade_drone");
    public static final Identifier BOMB_DRONE_ID = SparkStrength.id("bomb_drone");
    private static EntityType<M67GrenadeEntity> m67;
    private static EntityType<CapsuleEntity> capsule;
    private static EntityType<CaptureDeviceEntity> captureDevice;
    private static EntityType<CoolingOilEntity> coolingOil;
    private static EntityType<AromaOrbEntity> aromaOrb;
    private static EntityType<DroneEntity> grenadeDrone;
    private static EntityType<DroneEntity> bombDrone;
    private static boolean registered;

    private SparkStrengthEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        capsule = Registry.register(
                Registries.ENTITY_TYPE,
                CAPSULE_ID,
                EntityType.Builder.<CapsuleEntity>create(CapsuleEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .build(CAPSULE_ID.toString())
        );
        captureDevice = Registry.register(
                Registries.ENTITY_TYPE,
                CAPTURE_DEVICE_ID,
                EntityType.Builder.<CaptureDeviceEntity>create(CaptureDeviceEntity::new, SpawnGroup.MISC)
                        .dimensions(0.35F, 0.08F)
                        .maxTrackingRange(8)
                        .trackingTickInterval(10)
                        .build(CAPTURE_DEVICE_ID.toString())
        );
        m67 = Registry.register(
                Registries.ENTITY_TYPE,
                M67_ID,
                EntityType.Builder.<M67GrenadeEntity>create(M67GrenadeEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(8)
                        .trackingTickInterval(1)
                        .disableSaving()
                        .disableSummon()
                        .build(M67_ID.toString())
        );
        coolingOil = Registry.register(
                Registries.ENTITY_TYPE,
                COOLING_OIL_ID,
                EntityType.Builder.<CoolingOilEntity>create(CoolingOilEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .disableSaving()
                        .disableSummon()
                        .build(COOLING_OIL_ID.toString())
        );
        aromaOrb = Registry.register(
                Registries.ENTITY_TYPE,
                AROMA_ORB_ID,
                EntityType.Builder.<AromaOrbEntity>create(AromaOrbEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .disableSaving()
                        .disableSummon()
                        .build(AROMA_ORB_ID.toString())
        );
        // Transient round entities; tracked every tick for smooth remote flight. Range is widened further for the
        // pilot by the drone streaming hooks.
        // 临时回合实体；每刻追踪以保证远端飞行平滑。驾驶者的追踪范围由无人机串流钩子进一步放宽。
        grenadeDrone = Registry.register(
                Registries.ENTITY_TYPE,
                GRENADE_DRONE_ID,
                EntityType.Builder.<DroneEntity>create(DroneEntity::new, SpawnGroup.MISC)
                        .dimensions(0.7F, 0.3F)
                        .maxTrackingRange(16)
                        .trackingTickInterval(1)
                        .alwaysUpdateVelocity(true)
                        .disableSaving()
                        .disableSummon()
                        .build(GRENADE_DRONE_ID.toString())
        );
        bombDrone = Registry.register(
                Registries.ENTITY_TYPE,
                BOMB_DRONE_ID,
                EntityType.Builder.<DroneEntity>create(DroneEntity::new, SpawnGroup.MISC)
                        .dimensions(0.6F, 0.3F)
                        .maxTrackingRange(16)
                        .trackingTickInterval(1)
                        .alwaysUpdateVelocity(true)
                        .disableSaving()
                        .disableSummon()
                        .build(BOMB_DRONE_ID.toString())
        );
        registered = true;
    }

    public static EntityType<M67GrenadeEntity> m67() {
        if (m67 == null) {
            throw new IllegalStateException("SparkStrength entities are not registered yet");
        }
        return m67;
    }

    public static EntityType<CapsuleEntity> capsule() {
        if (capsule == null) {
            throw new IllegalStateException("SparkStrength entities are not registered yet");
        }
        return capsule;
    }

    public static EntityType<CaptureDeviceEntity> captureDevice() {
        if (captureDevice == null) {
            throw new IllegalStateException("SparkStrength entities are not registered yet");
        }
        return captureDevice;
    }

    public static EntityType<CoolingOilEntity> coolingOil() {
        if (coolingOil == null) {
            throw new IllegalStateException("SparkStrength entities are not registered yet");
        }
        return coolingOil;
    }

    public static EntityType<AromaOrbEntity> aromaOrb() {
        if (aromaOrb == null) {
            throw new IllegalStateException("SparkStrength entities are not registered yet");
        }
        return aromaOrb;
    }

    public static EntityType<DroneEntity> grenadeDrone() {
        if (grenadeDrone == null) {
            throw new IllegalStateException("SparkStrength entities are not registered yet");
        }
        return grenadeDrone;
    }

    public static EntityType<DroneEntity> bombDrone() {
        if (bombDrone == null) {
            throw new IllegalStateException("SparkStrength entities are not registered yet");
        }
        return bombDrone;
    }
}
