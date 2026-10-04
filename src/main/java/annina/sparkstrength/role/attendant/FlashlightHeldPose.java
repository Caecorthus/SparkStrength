package annina.sparkstrength.role.attendant;

/**
 * Pure geometry of a flashlight held in third person with the aimed arm pose: where the lens of the rendered item
 * sits relative to the player's render position. It replays vanilla's player model chain step by step
 * (LivingEntityRenderer, PlayerEntityRenderer, BipedEntityModel arm pivots, HeldItemFeatureRenderer, the item's
 * display transform and ItemRenderer's -0.5 recentre), so the light origin lands on the lens the player model draws.
 * No Minecraft types; angles in degrees unless noted.
 * 第三人称瞄准姿态下手持手电的纯几何：渲染出的物品镜片相对玩家渲染位置的坐标。按原版玩家模型链逐步复现
 * （LivingEntityRenderer、PlayerEntityRenderer、BipedEntityModel 手臂枢轴、HeldItemFeatureRenderer、物品展示变换与
 * ItemRenderer 的 -0.5 居中），使光源原点落在玩家模型绘制的镜片上。不依赖 Minecraft 类型；角度除特别说明外均为度。
 *
 * <p>Aim contract shared with the client arm mixin: the holding arm's pitch is {@code headPitch + display.rotX} and its
 * yaw is the head's net yaw, so the item's +Y (tail to lens) runs exactly along the look direction whenever the display
 * transform only rotates about X. 与客户端手臂 mixin 共享的瞄准约定：持手电手臂的俯仰为头部俯仰加展示变换 X 旋转，
 * 偏航为头部相对偏航；只要展示变换仅绕 X 旋转，物品 +Y（尾到镜片）就与视线方向完全一致。</p>
 */
public final class FlashlightHeldPose {
    /** PlayerEntityRenderer#scale. / 玩家模型缩放。 */
    public static final double PLAYER_MODEL_SCALE = 0.9375;
    /** LivingEntityRenderer's translate after flipping the model. / 模型翻转后的平移量。 */
    public static final double MODEL_Y_OFFSET = 1.501;
    /** BipedEntityModel arm pivot in model pixels (x = ±5, z = 0; y = 2 standing, 5.2 sneaking). / 手臂枢轴（模型像素）。 */
    public static final double ARM_PIVOT_X = 5.0;
    public static final double ARM_PIVOT_Y = 2.0;
    public static final double ARM_PIVOT_Y_SNEAKING = 5.2;
    /** PlayerEntityRenderer#getPositionOffset while sneaking, before entity scale. / 潜行时的渲染位置下移量。 */
    public static final double SNEAK_DROP_BLOCKS = 2.0 / 16.0;
    /** Lens centre in flashlight.json model pixels. / flashlight.json 中镜片中心（模型像素）。 */
    public static final double LENS_X = 8.0;
    public static final double LENS_Y = 13.0;
    public static final double LENS_Z = 8.0;
    /** Tail centre in flashlight.json model pixels (with the lens, it defines the item axis). / 尾盖中心，与镜片共同确定物品轴线。 */
    public static final double TAIL_Y = 2.0;

    private FlashlightHeldPose() {
    }

    /**
     * Third-person display transform of one hand, as JSON gives it: rotation in degrees, translation in blocks
     * (JSON pixels / 16) and scale. 单手第三人称展示变换：旋转（度）、平移（格，即 JSON 像素 / 16）与缩放。
     */
    public record Display(double rotX, double rotY, double rotZ,
                          double translateX, double translateY, double translateZ,
                          double scaleX, double scaleY, double scaleZ) {
        /** flashlight.json thirdperson_righthand/lefthand at the time of writing. / 编写时 flashlight.json 的第三人称变换。 */
        public static final Display FLASHLIGHT = new Display(-15.0, 0.0, 0.0,
                0.0, 1.5 / 16.0, 1.75 / 16.0, 0.85, 0.85, 0.85);
    }

    /** Arm pitch in radians that points the held item along {@code headPitchRadians}. / 使物品指向头部俯仰的手臂俯仰（弧度）。 */
    public static double aimArmPitch(double headPitchRadians, Display display) {
        return headPitchRadians + Math.toRadians(display.rotX());
    }

    /**
     * Offset of the lens from the player's render position (feet, frame-interpolated), in world axes.
     * 镜片相对玩家渲染位置（脚底、逐帧插值）的偏移，世界坐标轴。
     *
     * @param netHeadYaw head yaw relative to the body, as the model receives it / 头部相对身体的偏航
     * @param scale      entity scale attribute (1 for a normal player) / 实体缩放属性
     */
    public static void lensOffset(double bodyYaw, double netHeadYaw, double headPitch, boolean rightArm,
                                  boolean sneaking, double scale, Display display, double[] out) {
        modelPoint(bodyYaw, netHeadYaw, headPitch, rightArm, sneaking, scale, display, LENS_X, LENS_Y, LENS_Z, out);
    }

    /**
     * Any point of the held item model (in model pixels) through the same chain as {@link #lensOffset}.
     * 将物品模型中任意一点（模型像素）经同一变换链映射到世界偏移。
     */
    public static void modelPoint(double bodyYaw, double netHeadYaw, double headPitch, boolean rightArm,
                                  boolean sneaking, double scale, Display display,
                                  double modelX, double modelY, double modelZ, double[] out) {
        // Applied innermost first, in place in out: each step mirrors one MatrixStack call, read bottom-up.
        // 由内向外、直接在 out 中原地应用：每一步对应一次 MatrixStack 调用（自下而上阅读）。
        double[] p = out;
        p[0] = modelX / 16.0 - 0.5;
        p[1] = modelY / 16.0 - 0.5;
        p[2] = modelZ / 16.0 - 0.5;

        // Transformation#apply: translate(mirror * tx, ty, tz) * rotationXYZ(rx, ±ry, ±rz) * scale.
        p[0] *= display.scaleX();
        p[1] *= display.scaleY();
        p[2] *= display.scaleZ();
        double mirror = rightArm ? 1.0 : -1.0;
        rotateZ(p, Math.toRadians(display.rotZ() * mirror));
        rotateY(p, Math.toRadians(display.rotY() * mirror));
        rotateX(p, Math.toRadians(display.rotX()));
        p[0] += mirror * display.translateX();
        p[1] += display.translateY();
        p[2] += display.translateZ();

        // HeldItemFeatureRenderer#renderItem: rotX(-90) * rotY(180) * translate(±1/16, 0.125, -0.625).
        p[0] += mirror / 16.0;
        p[1] += 0.125;
        p[2] += -0.625;
        rotateY(p, Math.PI);
        rotateX(p, -Math.PI / 2.0);

        // ModelPart#rotate of the aimed arm: translate(pivot / 16) * rotationZYX(0, yaw, pitch).
        rotateX(p, aimArmPitch(Math.toRadians(headPitch), display));
        rotateY(p, Math.toRadians(netHeadYaw));
        p[0] += (rightArm ? -ARM_PIVOT_X : ARM_PIVOT_X) / 16.0;
        p[1] += (sneaking ? ARM_PIVOT_Y_SNEAKING : ARM_PIVOT_Y) / 16.0;

        // LivingEntityRenderer#render: scale(s) * rotY(180 - bodyYaw) * scale(-1, -1, 1) * scale(0.9375)
        // * translate(0, -1.501, 0); EntityRenderDispatcher adds the sneaking position offset outside.
        p[1] -= MODEL_Y_OFFSET;
        p[0] *= -PLAYER_MODEL_SCALE;
        p[1] *= -PLAYER_MODEL_SCALE;
        p[2] *= PLAYER_MODEL_SCALE;
        rotateY(p, Math.toRadians(180.0 - bodyYaw));
        p[0] *= scale;
        p[1] *= scale;
        p[2] *= scale;
        if (sneaking) {
            p[1] -= SNEAK_DROP_BLOCKS * scale;
        }
    }

    private static void rotateX(double[] p, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double y = p[1] * cos - p[2] * sin;
        double z = p[1] * sin + p[2] * cos;
        p[1] = y;
        p[2] = z;
    }

    private static void rotateY(double[] p, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = p[0] * cos + p[2] * sin;
        double z = -p[0] * sin + p[2] * cos;
        p[0] = x;
        p[2] = z;
    }

    private static void rotateZ(double[] p, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = p[0] * cos - p[1] * sin;
        double y = p[0] * sin + p[1] * cos;
        p[0] = x;
        p[1] = y;
    }
}
