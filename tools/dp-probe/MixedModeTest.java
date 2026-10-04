import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsSpace.BroadphaseType;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

/**
 * Simulates the proposed deployment: ONE Java/Kotlin codebase that always uses
 * the double-precision (Dp) accessors, running on DIFFERENT natives:
 *   - desktop : DpMtRelease  -> true double precision
 *   - android : SpRelease    -> same code, silently degrades to float
 *
 * Success criteria:
 *   (1) the Dp symbols must resolve on BOTH natives (no UnsatisfiedLinkError)
 *   (2) both float and Dp accessors must keep working on both natives
 *   (3) the exact same class file runs unmodified in both cases
 */
public class MixedModeTest {

    public static void main(String[] args) throws Exception {
        System.load(args[0]);

        System.out.println("isDoublePrecision = "
                + com.jme3.bullet.util.NativeLibrary.isDoublePrecision());
        System.out.println("isThreadSafe      = "
                + com.jme3.bullet.util.NativeLibrary.isThreadSafe());
        System.out.println();

        PhysicsSpace space = new PhysicsSpace(
                new Vector3f(-1.0e8f, -1.0e8f, -1.0e8f),
                new Vector3f(1.0e8f, 1.0e8f, 1.0e8f),
                BroadphaseType.DBVT);
        space.setGravity(new Vector3f(0f, -9.81f, 0f));

        // ---- pass 1: Dp accessors (this is what the mod would use everywhere)
        double targetX = 12345678.3;   // ~1.2e7 blocks: float ULP is 1.0 here
        PhysicsRigidBody a = new PhysicsRigidBody(
                new BoxCollisionShape(new Vector3f(0.3f, 0.3f, 0.3f)), 1f);
        a.setPhysicsLocationDp(new Vec3d(targetX, 10.0, -0.3));
        a.setLinearVelocityDp(new Vec3d(0.25, 0.0, -0.125));
        a.setPhysicsRotationDp(new Quatd(0.0, 0.0, 0.0, 1.0));
        space.addCollisionObject(a);

        Vec3d backLoc = a.getPhysicsLocationDp(null);
        Vec3d backVel = a.getLinearVelocityDp(null);
        Quatd backRot = a.getPhysicsRotationDp(null);

        System.out.println("[Dp accessors]");
        System.out.printf("  setPhysicsLocationDp  : requested %.1f -> stored %.6f  (err %.6f)%n",
                targetX, backLoc.x, Math.abs(backLoc.x - targetX));
        System.out.printf("  setLinearVelocityDp   : stored (%.6f, %.6f, %.6f)%n",
                backVel.x, backVel.y, backVel.z);
        System.out.printf("  setPhysicsRotationDp  : stored w=%.6f%n", backRot.w);

        // ---- pass 2: float accessors on the SAME body (must still work)
        Vector3f fLoc = a.getPhysicsLocation(null);
        Quaternion fRot = a.getPhysicsRotation((Quaternion) null);
        System.out.println("[float accessors, same body]");
        System.out.printf("  getPhysicsLocation    : (%.3f, %.3f, %.3f)%n",
                fLoc.x, fLoc.y, fLoc.z);
        System.out.printf("  getPhysicsRotation    : ok, w=%.3f%n", fRot.getW());

        // ---- pass 3: step the simulation and read back in both precisions
        for (int i = 0; i < 60; i++) {
            space.update(1f / 60f, 4);
        }
        Vec3d locAfter = a.getPhysicsLocationDp(null);
        System.out.println("[after 60 ticks]");
        System.out.printf("  Y (double path) = %.6f   X = %.6f%n", locAfter.y, locAfter.x);

        System.out.println();
        System.out.println("RESULT: same class file ran with both Dp and float accessors -- OK");
    }
}
