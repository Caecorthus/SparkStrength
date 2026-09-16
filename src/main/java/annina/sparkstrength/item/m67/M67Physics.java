package annina.sparkstrength.item.m67;

public final class M67Physics {
    public static final double LAUNCH_SPEED = 0.98;
    public static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.99;
    private static final double WALL_RESTITUTION = 0.35;
    private static final double GROUND_RESTITUTION = 0.25;
    private static final double MIN_BOUNCE_SPEED = 0.12;
    private static final double LANDING_DRAG = 0.70;
    private static final double ROLL_DRAG = 0.75;
    private static final double STOP_SPEED = 0.01;
    private static final double COLLISION_EPSILON = 1.0e-7;

    private M67Physics() {
    }

    public record Vector(double x, double y, double z) {
    }

    public record Collision(boolean x, boolean y, boolean z) {
    }

    public record Motion(Vector velocity, boolean grounded, boolean hasLanded, boolean firstLanding) {
    }

    public static Vector launch(double pitchDegrees, double yawDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        return new Vector(-Math.sin(yaw) * Math.cos(pitch) * LAUNCH_SPEED,
                -Math.sin(pitch) * LAUNCH_SPEED, Math.cos(yaw) * Math.cos(pitch) * LAUNCH_SPEED);
    }

    // Gravity also probes support while resting; removed floors must resume falling. / 静止时仍用重力探测支撑，地板移除后继续下落。
    public static Vector requestedMovement(Vector velocity) {
        return new Vector(velocity.x(), velocity.y() - GRAVITY, velocity.z());
    }

    public static Collision collision(Vector requested, Vector actual) {
        return new Collision(Math.abs(requested.x() - actual.x()) > COLLISION_EPSILON,
                Math.abs(requested.y() - actual.y()) > COLLISION_EPSILON,
                Math.abs(requested.z() - actual.z()) > COLLISION_EPSILON);
    }

    public static Motion afterCollision(Vector requested, Collision collision, boolean hasLanded) {
        boolean landing = collision.y() && requested.y() < 0.0;
        boolean grounded = landing && -requested.y() < MIN_BOUNCE_SPEED;
        double x = collision.x() ? -requested.x() * WALL_RESTITUTION : requested.x();
        double z = collision.z() ? -requested.z() * WALL_RESTITUTION : requested.z();
        double y = requested.y();
        if (landing) {
            y = grounded ? 0.0 : -y * GROUND_RESTITUTION;
        } else if (collision.y()) {
            y = -y * WALL_RESTITUTION;
        }
        double drag = grounded ? ROLL_DRAG : landing ? LANDING_DRAG : AIR_DRAG;
        x *= drag;
        z *= drag;
        if (grounded && x * x + z * z < STOP_SPEED * STOP_SPEED) {
            x = 0.0;
            z = 0.0;
        }
        return new Motion(new Vector(x, y, z), grounded, hasLanded || landing, landing && !hasLanded);
    }
}
