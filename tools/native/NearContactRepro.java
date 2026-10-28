import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.SolverMode;
import com.jme3.bullet.StepFlag;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PersistentManifolds;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Vector3f;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Minimal single-file reproducer: wrapping a shape in a {@code CompoundCollisionShape} must not
 * change the contacts it makes with the same geometry left unwrapped.
 *
 * <p>Every row uses the same sphere, the same mass, the same load and the same initial position.
 * The only variable is which collision shape is wrapped in a compound, so any difference in the
 * contact count, the applied impulse or the end-of-step velocity comes from the collision algorithm
 * that gets selected, not from the scene.</p>
 *
 * <p>The ground is exposed both as one big box and as a compound of small boxes tiling the same
 * volume. The two are geometrically identical; the small-box variant is what terrain looks like in
 * an engine that stores chunks as compounds, and it is the variant where a per-child tolerance
 * differs most from the per-pair one.</p>
 *
 * <p>Compile and run against Libbulletjme only (no engine, no test framework). JDK 17 or newer is
 * required because the class uses records.</p>
 *
 * <pre>
 * javac -cp Libbulletjme.jar NearContactRepro.java
 * java -cp "Libbulletjme.jar;." -Dmm.native=C:\path\to\bulletjme.dll NearContactRepro   (Windows)
 * java -cp "Libbulletjme.jar:." -Dmm.native=/path/to/libbulletjme.so  NearContactRepro   (Linux, macOS)
 * </pre>
 *
 * <p>{@code -Dmm.native} points at the native library to exercise, so a stock build and a locally
 * rebuilt one can be compared without moving any files.</p>
 */
public final class NearContactRepro {
    /** Wheel radius in metres. A 0.84375 m sphere has an angular-motion disc of 1.461 m. */
    private static final float RADIUS = 0.84375f;
    private static final float MASS = 50f;
    /** Downward load applied as a central force every step (one wheel's spring preload). */
    private static final float LOAD = 12500f;
    private static final float GRAVITY = 9.81f;
    private static final float DT = 0.01f;

    /**
     * Surface gaps to sweep, in metres. The band of interest is the one the dispatcher accepts but
     * the compound layer may drop: 0 to {@code disc * gContactBreakingThreshold} = 29.2 mm here.
     */
    private static final double[] GAPS = {
            -1e-6, 0d, 1e-6, 1e-3, 0.005, 0.01, 0.015, 0.02, 0.021, 0.025, 0.029, 0.035, 0.04
    };

    /** How the ground's 16 m x 1 m x 16 m slab is expressed. */
    private enum Ground {
        /** one box, halfExtents (8, 0.5, 8). */
        BOX,
        /** a compound holding 256 boxes of halfExtents (0.5, 0.5, 0.5) tiling the same slab. */
        SMALL_BOXES
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("com.jme3").setLevel(java.util.logging.Level.WARNING);

        String nativePath = System.getProperty("mm.native");
        if (nativePath == null) {
            System.err.println("usage: java -Dmm.native=<path to bulletjme native> NearContactRepro");
            System.exit(2);
        }
        System.load(Path.of(nativePath).toAbsolutePath().toString());

        System.out.printf(Locale.ROOT, "native : %s%n", nativePath);
        System.out.printf(Locale.ROOT, "         version %s%n", NativeLibrary.versionNumber());
        System.out.printf(Locale.ROOT,
                "scene  : wheel r=%.5f m, mass %.0f kg, load %.0f N, gravity %.2f m/s^2, dt %.3f s%n",
                RADIUS, MASS, LOAD, GRAVITY, DT);
        System.out.printf(Locale.ROOT,
                "         ground 16 x 1 x 16 m slab, top face at y=0, friction 0, restitution 0%n");
        System.out.printf(Locale.ROOT,
                "         velocity gained per step without support: %.4f m/s%n%n",
                (LOAD / MASS + GRAVITY) * DT);

        String header = "%-10s | %-19s | %-19s | %-19s | %-19s";
        System.out.printf(Locale.ROOT, header + "%n", "gap (m)", "wheel plain", "wheel compound",
                "wheel plain", "wheel compound");
        System.out.printf(Locale.ROOT, header + "%n", "", "ground box", "ground box",
                "ground 256 boxes", "ground 256 boxes");
        System.out.printf(Locale.ROOT, header + "%n", "", "pts  imp   dv", "pts  imp   dv",
                "pts  imp   dv", "pts  imp   dv");
        System.out.println("-".repeat(99));

        for (double gap : GAPS) {
            System.out.printf(Locale.ROOT, "%-10s | %-19s | %-19s | %-19s | %-19s%n",
                    String.format(Locale.ROOT, "%+.6f", gap),
                    cell(false, Ground.BOX, gap),
                    cell(true, Ground.BOX, gap),
                    cell(false, Ground.SMALL_BOXES, gap),
                    cell(true, Ground.SMALL_BOXES, gap));
        }

        System.out.println();
        System.out.println("Starting 40 mm above the surface, two steps (crossing the 29.2 mm threshold):");
        report("wheel plain, ground box", false, Ground.BOX);
        report("wheel compound, ground box", true, Ground.BOX);
        report("wheel plain, ground 256 boxes", false, Ground.SMALL_BOXES);
        report("wheel compound, ground 256 boxes", true, Ground.SMALL_BOXES);
    }

    private static void report(String label, boolean wheelCompound, Ground ground) {
        System.out.printf(Locale.ROOT, "  %-32s step1=%-21s step2=%s%n",
                label, step(wheelCompound, ground, 0.04, 1), step(wheelCompound, ground, 0.04, 2));
    }

    private static String cell(boolean wheelCompound, Ground ground, double gap) {
        return step(wheelCompound, ground, gap, 1).toString();
    }

    /** Steps a fresh rig and returns the state at the end of the requested step. */
    private static Sample step(boolean wheelCompound, Ground ground, double gap, int steps) {
        try (Rig rig = new Rig(wheelCompound, ground, gap)) {
            Sample sample = null;
            for (int i = 0; i < steps; i++) {
                sample = rig.advance();
            }
            return sample;
        }
    }

    private static CollisionShape wrap(CollisionShape shape, boolean compound) {
        if (!compound) {
            return shape;
        }
        CompoundCollisionShape result = new CompoundCollisionShape();
        result.addChildShape(shape, new Vector3f());
        return result;
    }

    private static CollisionShape groundShape(Ground ground) {
        if (ground == Ground.BOX) {
            return new BoxCollisionShape(new Vector3f(8f, 0.5f, 8f));
        }
        CompoundCollisionShape compound = new CompoundCollisionShape();
        BoxCollisionShape unit = new BoxCollisionShape(new Vector3f(0.5f, 0.5f, 0.5f));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                compound.addChildShape(unit, new Vector3f(x - 7.5f, 0f, z - 7.5f));
            }
        }
        return compound;
    }

    private record Sample(int points, float impulse, float verticalVelocity) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d  %7.2f  %+7.4f", points, impulse, verticalVelocity);
        }
    }

    private static final class Rig implements AutoCloseable {
        final PhysicsSpace space;
        final PhysicsRigidBody ground;
        final PhysicsRigidBody wheel;

        Rig(boolean wheelCompound, Ground groundKind, double gap) {
            space = new PhysicsSpace(new Vector3f(-1000f, -1000f, -1000f),
                    new Vector3f(1000f, 1000f, 1000f), PhysicsSpace.BroadphaseType.DBVT,
                    NativeLibrary.countThreads(), new CollisionConfiguration(8192, 1));
            space.setGravity(new Vector3f(0f, -GRAVITY, 0f));
            space.getSolverInfo().setMode(SolverMode.SIMD | SolverMode.WarmStart
                    | SolverMode.CacheFriendly | SolverMode.CacheDirection
                    | SolverMode.ArticulatedWarmStart | SolverMode.Interleave);

            ground = new PhysicsRigidBody(groundShape(groundKind), 0f);
            ground.setPhysicsLocation(new Vector3f(0f, -0.5f, 0f));
            ground.setFriction(0f);
            ground.setRestitution(0f);
            space.addCollisionObject(ground);

            wheel = new PhysicsRigidBody(wrap(new SphereCollisionShape(RADIUS), wheelCompound), MASS);
            // float precision is ~6e-8 m at this magnitude, far below the smallest gap in GAPS
            wheel.setPhysicsLocation(new Vector3f(0f, RADIUS + (float) gap, 0f));
            wheel.setDamping(0f, 0f);
            wheel.setSleepingThresholds(0f, 0f);
            wheel.setFriction(0f);
            wheel.setRestitution(0f);
            space.addCollisionObject(wheel);
        }

        Sample advance() {
            wheel.applyCentralForce(new Vector3f(0f, -LOAD, 0f));
            space.update(DT, 0, StepFlag.contactProcessed);
            int points = 0;
            float impulse = 0f;
            for (long manifold : space.listManifoldIds()) {
                for (int i = 0; i < PersistentManifolds.countPoints(manifold); i++) {
                    long id = PersistentManifolds.getPointId(manifold, i);
                    points++;
                    impulse += ManifoldPoints.getAppliedImpulse(id);
                }
            }
            return new Sample(points, impulse, wheel.getLinearVelocity(null).y);
        }

        @Override
        public void close() {
            space.removeCollisionObject(wheel);
            space.removeCollisionObject(ground);
            space.destroy();
        }
    }
}
