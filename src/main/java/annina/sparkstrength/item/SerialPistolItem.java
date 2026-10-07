package annina.sparkstrength.item;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.network.SerialPistolShootC2SPayload;
import annina.sparkstrength.role.serialkiller.SerialKillerConstants;
import dev.doctor4t.wathe.item.RevolverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * 连环杀手专用枪械。继承 Wathe 左轮以复用第三人称枪械姿势和枪械模型，
 * 但开火包通过反射发送，保证 main 源集不硬依赖 Fabric client networking。
 */
public final class SerialPistolItem extends RevolverItem {
    private final boolean leftHand;

    public SerialPistolItem(Settings settings, boolean leftHand) {
        super(settings);
        this.leftHand = leftHand;
    }

    public boolean isLeftHand() {
        return leftHand;
    }

    /** The main pistol fires only from the main hand, the left pistol only from the off hand. / 主手枪只在主手、左持枪只在副手开火。 */
    public static boolean isPistolForHand(ItemStack stack, Hand hand) {
        return hand == Hand.MAIN_HAND
                ? stack.isOf(SparkStrengthItems.serialPistol())
                : hand == Hand.OFF_HAND && stack.isOf(SparkStrengthItems.serialLeftPistol());
    }

    /** Either serial pistol, regardless of slot. / 任一把连环手枪（不论所在槽位）。 */
    public static boolean isSerialPistol(ItemStack stack) {
        return stack.getItem() instanceof SerialPistolItem;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            // Same target pick as the revolver (Wathe's 30-block getGunTarget, sleeping players on beds included), so
            // client hooks on getGunTarget apply alike (SparkWitch's active-Wraith pass-through and nearer Magician
            // puppet); SparkWitch's Seeker device step, which it wraps inside RevolverItem#use, is added through its
            // public API (unchanged pick without SparkWitch). The server re-validates everything.
            // 与左轮相同的选靶（Wathe 30 格 getGunTarget，包括床上睡觉的玩家），getGunTarget 上的客户端钩子同样生效（SparkWitch
            // 的激活冤魂穿透与更近的魔术师皮套）；SparkWitch 包装在 RevolverItem#use 内的搜寻者设备一步经其公开 API 补上
            // （未安装 SparkWitch 时选靶不变）。服务端会全部复核。
            HitResult aim = SparkWitchCompat.preferNearerGunWorldTarget(user, RevolverItem.getGunTarget(user),
                    SerialKillerConstants.PISTOL_RANGE_BLOCKS);
            int targetId = RevolverItem.resolveTargetFromHitResult(world, aim);
            sendShootPacket(new SerialPistolShootC2SPayload(targetId, hand));
            user.setPitch(user.getPitch() - 4.0F);
            // Wathe 的粒子类只在客户端分支加载；调用方式与左轮一致。
            RevolverItem.spawnHandParticle();
        }
        return TypedActionResult.consume(stack);
    }

    /** Crosshair helper: the player Wathe's revolver ray would hit, or null. / 准心用：Wathe 左轮射线会命中的玩家，或 null。 */
    public static EntityHitResult findTarget(PlayerEntity user) {
        HitResult result = RevolverItem.getGunTarget(user);
        return result instanceof EntityHitResult entityHitResult ? entityHitResult : null;
    }

    private static void sendShootPacket(SerialPistolShootC2SPayload payload) {
        try {
            Class<?> networking = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            Method send = networking.getMethod("send", net.minecraft.network.packet.CustomPayload.class);
            send.invoke(null, payload);
        } catch (ReflectiveOperationException ignored) {
            // Dedicated server /未加载 client 源集时不应因网络类缺失而崩溃。
        }
    }
}
