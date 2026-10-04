import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsSpace.BroadphaseType;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;

/**
 * End-to-end test: does a static ground box at a very distant coordinate
 * correctly support a falling dynamic body?
 *
 * Run once with an Sp native and once with a Dp native and compare.
 */
public class DistantTest {

    static PhysicsSpace space;

    public static void main(String[] args) throws Exception {
        System.load(args[0]);

        System.out.println("isDoublePrecision = "
                + com.jme3.bullet.util.NativeLibrary.isDoublePrecision());
        System.out.println("isThreadSafe      = "
                + com.jme3.bullet.util.NativeLibrary.isThreadSafe());
        System.out.println();

        space = new PhysicsSpace(
                new Vector3f(-1.0e9f, -1.0e9f, -1.0e9f),
                new Vector3f(1.0e9f, 1.0e9f, 1.0e9f),
                BroadphaseType.DBVT
        );        space.setGravity(new Vector3f(0f, -9.81f, 0f));

        System.out.printf("%-8s %-12s %-20s %-20s %-10s %-11s %s%n",
                "mode", "baseX", "requested X", "stored X", "posErr", "final Y", "rests on ground?");

        double[] bases = {0.0, 1.0e6, 1.0e7, 3.0e7};

        for (double base : bases) {
            runCase("float", base);
        }
        for (double base : bases) {
            runCase("double", base);
        }
    }

    /** @param mode "float" -> setPhysicsLocation(Vector3f); "double" -> setPhysicsLocationDp(Vec3d) */
    static void runCase(String mode, double baseX) {
        // ground: a big static box. Put its TOP surface at y = 0.
        PhysicsRigidBody ground
                = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(5f, 0.5f, 5f)), 0f);

        // We want the ball to land 0.3 blocks off a round coordinate,
        // which is precisely where floats lose the fractional part.
        double groundX = baseX + 0.3;
        double groundY = 0.0;
        double groundZ = 0.3;

        // ball: dynamic, starts 2 blocks above the ground top
        double ballX = groundX;
        double ballY = 2.0;
        double ballZ = groundZ;

        if (mode.equals("float")) {
            ground.setPhysicsLocation(new Vector3f(
                    (float) groundX, (float) groundY, (float) groundZ));
        } else {
            ground.setPhysicsLocationDp(new Vec3d(groundX, groundY, groundZ));
        }
        space.addCollisionObject(ground);

        PhysicsRigidBody ball
                = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(0.3f, 0.3f, 0.3f)), 1f);
        ball.setFriction(0.5f);
        ball.setRestitution(0f);
        ball.setSleepingThresholds(0f, 0f); // keep it awake so we can read Y
        if (mode.equals("float")) {
            ball.setPhysicsLocation(new Vector3f(
                    (float) ballX, (float) ballY, (float) ballZ));
        } else {
            ball.setPhysicsLocationDp(new Vec3d(ballX, ballY, ballZ));
        }
        space.addCollisionObject(ball);
        ball.activate();

        // read back what the engine actually stored
        double storedX;
        if (mode.equals("float")) {
            storedX = ball.getPhysicsLocation(null).x;
        } else {
            storedX = ball.getPhysicsLocationDp(null).x;
        }

        // simulate 3 seconds at 60 Hz
        for (int i = 0; i < 180; i++) {
            space.update(1f / 60f, 4);
        }

        double finalY = (mode.equals("float"))
                ? ball.getPhysicsLocation(null).y
                : ball.getPhysicsLocationDp(null).y;

        // ground box is 1.0 tall (half-height 0.5), centred at y=0 -> top at y=+0.5
        // ball half-height 0.3 -> a correct resting centre is 0.5 + 0.3 = 0.8
        boolean onGround = finalY > 0.6 && finalY < 1.1;

        double offsetErr = Math.abs(storedX - ballX);

        System.out.printf("%-8s %-12s %-20s %-20s %-10s %-11s %s%n",
                mode,
                fmt(baseX),
                fmt(ballX),
                fmt(storedX),
                String.format("%.4f", offsetErr),
                String.format("%.4f", finalY),
                onGround ? "yes" : (offsetErr > 0.25 ? "NO - position " + String.format("%.2f", offsetErr) + " off" : "NO"));

        space.removeCollisionObject(ball);
        space.removeCollisionObject(ground);
    }

    static String fmt(double d) {
        return String.format("%.6f", d);
    }
}
