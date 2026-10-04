import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsSpace.BroadphaseType;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;

/**
 * Throughput benchmark: many dynamic bodies colliding with each other and with
 * a large static terrain. Uses the double-precision code path exclusively
 * (setPhysicsLocationDp) so the SAME java code runs on Sp and Dp natives --
 * isolating the native arithmetic cost.
 */
public class BenchBig {

    static final int N = 4000;            // dynamic bodies
    static final int WARMUP_TICKS = 60;
    static final int MEASURE_TICKS = 300;

    public static void main(String[] args) throws Exception {
        System.load(args[0]);

        boolean dp = com.jme3.bullet.util.NativeLibrary.isDoublePrecision();
        System.out.println("isDoublePrecision = " + dp);
        System.out.println("countThreads      = "
                + com.jme3.bullet.util.NativeLibrary.countThreads());

        PhysicsSpace space = new PhysicsSpace(
                new Vector3f(-5000f, -5000f, -5000f),
                new Vector3f(5000f, 5000f, 5000f),
                BroadphaseType.DBVT);
        space.setGravity(new Vector3f(0f, -9.81f, 0f));

        // ---- static terrain: a grid of boxes forming a rough floor ----
        CompoundCollisionShape terrain = new CompoundCollisionShape();
        for (int gx = -8; gx <= 8; gx++) {
            for (int gz = -8; gz <= 8; gz++) {
                BoxCollisionShape cell
                        = new BoxCollisionShape(new Vector3f(4f, 0.5f, 4f));
                terrain.addChildShape(cell, new Vector3f(gx * 8f, -0.5f, gz * 8f));
            }
        }
        PhysicsRigidBody ground = new PhysicsRigidBody(terrain, 0f);
        ground.setPhysicsLocation(new Vector3f(0f, 0f, 0f));
        space.addCollisionObject(ground);

        // ---- dynamic bodies in a pile ----
        PhysicsRigidBody[] bodies = new PhysicsRigidBody[N];
        java.util.Random rng = new java.util.Random(1234);
        for (int i = 0; i < N; i++) {
            float hx = 0.3f + rng.nextFloat() * 0.4f;
            PhysicsRigidBody b = new PhysicsRigidBody(
                    new BoxCollisionShape(new Vector3f(hx, hx, hx)), 1f);
            b.setFriction(0.6f);
            b.setRestitution(0.05f);
            double x = (rng.nextDouble() - 0.5) * 40.0;
            double y = 2.0 + i * 0.75;
            double z = (rng.nextDouble() - 0.5) * 40.0;
            b.setPhysicsLocationDp(new Vec3d(x, y, z));
            space.addCollisionObject(b);
            bodies[i] = b;
        }

        // ---- warm up (JIT + broadphase tree construction) ----
        for (int i = 0; i < WARMUP_TICKS; i++) {
            space.update(1f / 60f, 4);
        }

        // ---- measure ----
        long t0 = System.nanoTime();
        for (int i = 0; i < MEASURE_TICKS; i++) {
            space.update(1f / 60f, 4);
        }
        long t1 = System.nanoTime();

        double totalMs = (t1 - t0) / 1.0e6;
        double perTickMs = totalMs / MEASURE_TICKS;

        // count how many bodies are still awake (sanity: work actually done)
        int awake = 0;
        for (PhysicsRigidBody b : bodies) {
            if (b.isActive()) {
                awake++;
            }
        }

        System.out.printf("bodies=%d  ticks=%d%n", N, MEASURE_TICKS);
        System.out.printf("total   = %.1f ms%n", totalMs);
        System.out.printf("per tick= %.4f ms   (%.1f fps-equivalent if physics were the only cost)%n",
                perTickMs, 1000.0 / perTickMs);
        System.out.printf("awake at end = %d / %d%n", awake, N);
        System.out.println("RESULT_MS_PER_TICK=" + String.format("%.5f", perTickMs));
    }
}
