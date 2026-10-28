<!-- Title: Wrapping a sphere in CompoundCollisionShape drops near contacts and changes support impulses -->
<!-- 本文件的注释行不会渲染；标题取上一行，其余全部是 issue 正文。 -->

## Summary

Two otherwise identical scenes, same geometry, same initial position, same forces. The only
difference is that in one scene the sphere is wrapped in a `CompoundCollisionShape` and in the
other it is used directly.

For the wrapped sphere the contact point disappears at **every tested positive gap within the
dispatcher's contact threshold** (29.2 mm for this sphere). At a 1 µm gap, a step that should carry
129.90 N·s of support carries 0, and the body ends the step at the full unsupported velocity.

## Expected behavior

Wrapping a shape in a compound must not change which contacts it makes with the same geometry left
unwrapped, nor the support those contacts carry. A 1 um separation should behave like a 1 um
separation, whether or not one side is a compound.

## Measured on the stock build

Stock Libbulletjme 23.1.0 release binary, no local patches.

Scene: wheel is `SphereCollisionShape(r = 0.84375)`, mass 50 kg, central force 12500 N downward
plus gravity 9.81 m/s^2, fixed step 10 ms, friction 0, restitution 0, no damping, no sleeping.
Ground is a static `BoxCollisionShape(8, 0.5, 8)`, top face at y = 0. Velocity gained per step
without support is 2.5981 m/s; a fully supported step ends at 0.

| gap | plain sphere | same sphere in a `CompoundCollisionShape` |
| ---: | --- | --- |
| -1 um | 1 contact, 129.91 N s, 0.0000 m/s | 1 contact, 129.91 N s, 0.0000 m/s |
| 0 | 1 contact, 129.90 N s, 0.0000 m/s | 1 contact, 129.90 N s, 0.0000 m/s |
| +1 um | 1 contact, 129.90 N s, -0.0001 m/s | **0 contacts, 0 N s, -2.5981 m/s** |
| +1 mm | 1 contact, 124.91 N s, -0.1000 m/s | **0 contacts, 0 N s, -2.5981 m/s** |
| +5 mm | 1 contact, 104.91 N s, -0.5000 m/s | **0 contacts, 0 N s, -2.5981 m/s** |
| +10 mm | 1 contact, 79.91 N s, -1.0000 m/s | **0 contacts, 0 N s, -2.5981 m/s** |
| +15 mm | 1 contact, 54.91 N s, -1.5000 m/s | **0 contacts, 0 N s, -2.5981 m/s** |
| +20 mm | 1 contact, 29.91 N s, -2.0000 m/s | **0 contacts, 0 N s, -2.5981 m/s** |
| +21 mm | 1 contact, 24.90 N s, -2.1000 m/s | **0 contacts, 0 N s, -2.5981 m/s** |
| +25 mm | 1 contact, 4.91 N s, -2.5000 m/s | **0 contacts, 0 N s, -2.5981 m/s** |
| +29 mm | 1 contact, 0.00 N s, -2.5981 m/s | 0 contacts, 0 N s, -2.5981 m/s |
| +35 mm | 0 contacts, -2.5981 m/s | 0 contacts, -2.5981 m/s |

The +20 mm row is the point: the unwrapped sphere still gets a contact point and 29.91 N s of
support, so the narrow phase does report near contacts well inside the 29.2 mm threshold. (The
solver treats these as speculative contacts: `penetration = distance + linearSlop` and
`velocityError -= penetration * invTimeStep`, so the step may close the gap but no more.) The
wrapped sphere gets nothing.

The compound can be on either side. Ground expressed as a compound of 256 boxes of 1 m, tiling the
same 16 x 1 x 16 m slab, with the sphere left plain:

| gap | plain sphere, compound ground |
| ---: | --- |
| -1 um | 4 contacts, 121.54 N s, -0.1723 m/s |
| +1 um | **0 contacts, 0 N s, -2.5981 m/s** |
| +20 mm | **0 contacts, 0 N s, -2.5981 m/s** |

(Four points instead of one is expected: four child boxes touch the sphere. The transition at
+1 um is the point of the table.)

Starting 40 mm above the surface, two steps:

| configuration | step 1 | step 2 |
| --- | --- | --- |
| plain sphere, box ground | 0 contacts, -2.5981 m/s | 1 contact, 189.71 N s, -1.4019 m/s |
| compound sphere, box ground | 0 contacts, -2.5981 m/s | **0 contacts, -5.1962 m/s** |

## Where the two thresholds come from

`btCollisionDispatcher::getNewManifold()` creates the manifold for a pair with

```
contactBreakingThreshold = min(shapeA->getContactBreakingThreshold(gContactBreakingThreshold),
                               shapeB->getContactBreakingThreshold(gContactBreakingThreshold))
```

where `btCollisionShape::getContactBreakingThreshold(default)` returns
`getAngularMotionDisc() * default` (flag `CD_USE_RELATIVE_CONTACT_BREAKING_THRESHOLD`, on by
default). For a 0.84375 m sphere that is `0.84375 * sqrt(3) * 0.02 = 29.2 mm`.

Both compound algorithms instead test child-pair AABBs padded only by
`btManifoldResult::m_closestPointDistanceThreshold`, which is 0 unless closest-point mode is on.
So the inner check requires actual overlap while the manifold tolerates up to 29.2 mm of
separation, and everything in between is dropped before any narrow-phase algorithm runs.

The sites are `btCompoundLeafCallback::ProcessChildShape`, the tree cull and the child-algorithm
cleanup loop in `btCompoundCollisionAlgorithm::processCollision`, and
`btCompoundCompoundLeafCallback::Process`, the `MycollideTT` call and the paired cleanup loop in
`btCompoundCompoundCollisionAlgorithm::processCollision`.

`m_resultOut->getPersistentManifold()->getContactBreakingThreshold()` cannot be relied on there.
A compound algorithm owns no manifold of its own (`m_sharedManifold` is normally null; manifolds
belong to the child algorithms). The result object may have no manifold before the first child is
processed, and after a sibling child has run it may still hold that child's manifold. So there is
no guarantee of a valid manifold for the child pair being tested, even though the pointer is
sometimes non-null.

## Reproducer

Single file, depends only on Libbulletjme, needs JDK 17 or newer (it uses records). `-Dmm.native`
selects the native library to exercise, so the stock build and a locally rebuilt one can be compared
without moving files.

```bash
javac -cp Libbulletjme.jar NearContactRepro.java
java -cp "Libbulletjme.jar;." -Dmm.native=C:\path\to\bulletjme.dll NearContactRepro   # Windows
java -cp "Libbulletjme.jar:." -Dmm.native=/path/to/libbulletjme.so  NearContactRepro   # Linux, macOS
```

<details>
<summary><code>NearContactRepro.java</code></summary>

```java
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
```

</details>

## Proposed fix

Reproduce the dispatcher's formula from the two object-level shapes and use it at all six culling
sites:

```cpp
static btScalar btPairContactTolerance(const btCollisionShape* shapeA, const btCollisionShape* shapeB)
{
    return btMin(shapeA->getContactBreakingThreshold(gContactBreakingThreshold),
                 shapeB->getContactBreakingThreshold(gContactBreakingThreshold));
}
```

then replace each `btVector3 extend(m_resultOut->m_closestPointDistanceThreshold, ...)` with
`m_closestPointDistanceThreshold + btPairContactTolerance(a, b)`.

For the dispatcher to agree, both shapes passed in should be the object-level ones: the root
compound on each side, not a child shape. Passing a child shape narrows the window — for a 1 m box
child the term becomes `min(1.461, 0.866) * 0.02 = 17.3 mm`, and the 20-29 mm band stays broken.
`btCompoundCompoundLeafCallback` exposes `m_compound0ColObjWrap` / `m_compound1ColObjWrap`;
`ProcessChildShape` has `m_compoundColObjWrap`. Note that the patch below passes the wrapper's
`getCollisionShape()`, which is the root shape for the one-level layouts measured here. In a nested
compound that shape can be a child compound, so the patch does not guarantee the object-level
threshold there; see "What the patch does and does not establish".

`m_closestPointDistanceThreshold` itself is left at 0: raising it switches both compound algorithms
to `BT_CLOSEST_POINT_ALGORITHMS` and destroys child algorithms and manifolds every step, which is a
different change.

<details>
<summary>Patch against <code>78b76008</code></summary>

```diff
diff --git a/src/main/native/bullet3/BulletCollision/CollisionDispatch/btCompoundCollisionAlgorithm.cpp b/src/main/native/bullet3/BulletCollision/CollisionDispatch/btCompoundCollisionAlgorithm.cpp
index 6f7ea014..4ff642ab 100644
--- a/src/main/native/bullet3/BulletCollision/CollisionDispatch/btCompoundCollisionAlgorithm.cpp
+++ b/src/main/native/bullet3/BulletCollision/CollisionDispatch/btCompoundCollisionAlgorithm.cpp
@@ -23,6 +23,21 @@ subject to the following restrictions:
 #include "btManifoldResult.h"
 #include "BulletCollision/CollisionDispatch/btCollisionObjectWrapper.h"
 
+//Altered source: child culling must accept the same near contacts the narrow
+//phase accepts. That tolerance is per pair -- btCollisionDispatcher scales
+//gContactBreakingThreshold by the shapes' angular motion disc (half the AABB
+//diagonal), which for this wheel's sphere is 1.46 m, i.e. 29.2 mm of near
+//contact. An intermediate build extending by the bare global 0.02 still
+//culled everything between 20 mm and 29.2 mm, so the wheel lost a substep of
+//support exactly there. The formula below reproduces the dispatcher's
+//getNewManifold for the default CD_USE_RELATIVE_CONTACT_BREAKING_THRESHOLD;
+//callers pass wrapper shapes, which are roots only for one-level layouts.
+static btScalar btPairContactTolerance(const btCollisionShape* shapeA, const btCollisionShape* shapeB)
+{
+	return btMin(shapeA->getContactBreakingThreshold(gContactBreakingThreshold),
+				 shapeB->getContactBreakingThreshold(gContactBreakingThreshold));
+}
+
 btShapePairCallback gCompoundChildShapePairCallback = 0;
 
 btCompoundCollisionAlgorithm::btCompoundCollisionAlgorithm(const btCollisionAlgorithmConstructionInfo& ci, const btCollisionObjectWrapper* body0Wrap, const btCollisionObjectWrapper* body1Wrap, bool isSwapped)
@@ -129,7 +144,8 @@ public:
 		btVector3 aabbMin0, aabbMax0;
 		childShape->getAabb(newChildWorldTrans, aabbMin0, aabbMax0);
 
-		btVector3 extendAabb(m_resultOut->m_closestPointDistanceThreshold, m_resultOut->m_closestPointDistanceThreshold, m_resultOut->m_closestPointDistanceThreshold);
+		btScalar extend = m_resultOut->m_closestPointDistanceThreshold + btPairContactTolerance(compoundShape, m_otherObjWrap->getCollisionShape());
+		btVector3 extendAabb(extend, extend, extend);
 		aabbMin0 -= extendAabb;
 		aabbMax0 += extendAabb;
 
@@ -284,7 +300,8 @@ void btCompoundCollisionAlgorithm::processCollision(const btCollisionObjectWrapp
 		btTransform otherInCompoundSpace;
 		otherInCompoundSpace = colObjWrap->getWorldTransform().inverse() * otherObjWrap->getWorldTransform();
 		otherObjWrap->getCollisionShape()->getAabb(otherInCompoundSpace, localAabbMin, localAabbMax);
-		btVector3 extraExtends(resultOut->m_closestPointDistanceThreshold, resultOut->m_closestPointDistanceThreshold, resultOut->m_closestPointDistanceThreshold);
+		btScalar extra = resultOut->m_closestPointDistanceThreshold + btPairContactTolerance(compoundShape, otherObjWrap->getCollisionShape());
+		btVector3 extraExtends(extra, extra, extra);
 		localAabbMin -= extraExtends;
 		localAabbMax += extraExtends;
 
@@ -329,6 +346,13 @@ void btCompoundCollisionAlgorithm::processCollision(const btCollisionObjectWrapp
 				childShape->getAabb(newChildWorldTrans, aabbMin0, aabbMax0);
 				otherObjWrap->getCollisionShape()->getAabb(otherObjWrap->getWorldTransform(), aabbMin1, aabbMax1);
 
+				//keep the algorithm alive as long as a near contact can still be reported
+				btScalar keep = btPairContactTolerance(compoundShape, otherObjWrap->getCollisionShape());
+				btVector3 keepVec(keep, keep, keep);
+				aabbMin0 -= keepVec;
+				aabbMax0 += keepVec;
+				aabbMin1 -= keepVec;
+				aabbMax1 += keepVec;
 				if (!TestAabbAgainstAabb2(aabbMin0, aabbMax0, aabbMin1, aabbMax1))
 				{
 					m_childCollisionAlgorithms[i]->~btCollisionAlgorithm();
diff --git a/src/main/native/bullet3/BulletCollision/CollisionDispatch/btCompoundCompoundCollisionAlgorithm.cpp b/src/main/native/bullet3/BulletCollision/CollisionDispatch/btCompoundCompoundCollisionAlgorithm.cpp
index 044b60db..2f41b02f 100644
--- a/src/main/native/bullet3/BulletCollision/CollisionDispatch/btCompoundCompoundCollisionAlgorithm.cpp
+++ b/src/main/native/bullet3/BulletCollision/CollisionDispatch/btCompoundCompoundCollisionAlgorithm.cpp
@@ -27,6 +27,17 @@ subject to the following restrictions:
 //USE_LOCAL_STACK will avoid most (often all) dynamic memory allocations due to resizing in processCollision and MycollideTT
 #define USE_LOCAL_STACK 1
 
+//Altered source: child culling must accept the same near contacts the narrow
+//phase accepts. That tolerance is per pair and computed by the dispatcher from
+//the shapes' angular motion disc; an intermediate build using the bare global
+//0.02 under-covers large shapes by an order of magnitude. See btPairContactTolerance in
+//btCompoundCollisionAlgorithm.cpp for the derivation.
+static btScalar btPairContactTolerance(const btCollisionShape* shapeA, const btCollisionShape* shapeB)
+{
+	return btMin(shapeA->getContactBreakingThreshold(gContactBreakingThreshold),
+				 shapeB->getContactBreakingThreshold(gContactBreakingThreshold));
+}
+
 btShapePairCallback gCompoundCompoundChildShapePairCallback = 0;
 
 btCompoundCompoundCollisionAlgorithm::btCompoundCompoundCollisionAlgorithm(const btCollisionAlgorithmConstructionInfo& ci, const btCollisionObjectWrapper* body0Wrap, const btCollisionObjectWrapper* body1Wrap, bool isSwapped)
@@ -145,7 +156,8 @@ struct btCompoundCompoundLeafCallback : btDbvt::ICollide
 		childShape0->getAabb(newChildWorldTrans0, aabbMin0, aabbMax0);
 		childShape1->getAabb(newChildWorldTrans1, aabbMin1, aabbMax1);
 
-		btVector3 thresholdVec(m_resultOut->m_closestPointDistanceThreshold, m_resultOut->m_closestPointDistanceThreshold, m_resultOut->m_closestPointDistanceThreshold);
+		btScalar threshold = m_resultOut->m_closestPointDistanceThreshold + btPairContactTolerance(m_compound0ColObjWrap->getCollisionShape(), m_compound1ColObjWrap->getCollisionShape());
+		btVector3 thresholdVec(threshold, threshold, threshold);
 
 		aabbMin0 -= thresholdVec;
 		aabbMax0 += thresholdVec;
@@ -342,7 +354,7 @@ void btCompoundCompoundCollisionAlgorithm::processCollision(const btCollisionObj
 	btCompoundCompoundLeafCallback callback(col0ObjWrap, col1ObjWrap, this->m_dispatcher, dispatchInfo, resultOut, this->m_childCollisionAlgorithmCache, m_sharedManifold);
 
 	const btTransform xform = col0ObjWrap->getWorldTransform().inverse() * col1ObjWrap->getWorldTransform();
-	MycollideTT(tree0->m_root, tree1->m_root, xform, &callback, resultOut->m_closestPointDistanceThreshold);
+	MycollideTT(tree0->m_root, tree1->m_root, xform, &callback, resultOut->m_closestPointDistanceThreshold + btPairContactTolerance(col0ObjWrap->getCollisionShape(), col1ObjWrap->getCollisionShape()));
 
 	//printf("#compound-compound child/leaf overlap =%d                      \r",callback.m_numOverlapPairs);
 
@@ -374,7 +386,8 @@ void btCompoundCompoundCollisionAlgorithm::processCollision(const btCollisionObj
 					newChildWorldTrans0 = col0ObjWrap->getWorldTransform() * childTrans0;
 					childShape0->getAabb(newChildWorldTrans0, aabbMin0, aabbMax0);
 				}
-				btVector3 thresholdVec(resultOut->m_closestPointDistanceThreshold, resultOut->m_closestPointDistanceThreshold, resultOut->m_closestPointDistanceThreshold);
+				btScalar keepThreshold = resultOut->m_closestPointDistanceThreshold + btPairContactTolerance(col0ObjWrap->getCollisionShape(), col1ObjWrap->getCollisionShape());
+				btVector3 thresholdVec(keepThreshold, keepThreshold, keepThreshold);
 				aabbMin0 -= thresholdVec;
 				aabbMax0 += thresholdVec;
 				{
```

</details>

### What the patch does and does not establish

- It restores the near contacts at the tested positive gaps: with the ground as 256 boxes, contacts
  reappear from +1 um through +25 mm, where the stock build reports none. Within the same ground
  representation, the wrapped and unwrapped sphere now agree. Different terrain decompositions can
  still produce different impulses; the patch does not claim otherwise.
- It is **not** a complete reproduction of the dispatcher's threshold in every layout. The wheel in
  these sweeps is a compound with one centred child; the compound ground has 256 children. Offset
  wheel children, nested compounds and other arrangements are not verified.
- With `CD_USE_RELATIVE_CONTACT_BREAKING_THRESHOLD` off the dispatcher uses the bare
  `gContactBreakingThreshold` (0.02). The helper keeps scaling by disc, which under-covers shapes
  with a disc below 1; it does not merely over-extend.
- Cost is not benchmarked. The added extent is bounded by the smaller of the two discs, but for two
  large compounds that can still be tens of centimetres and more live child algorithms.

## Open questions

1. **Cost.** Is the extra extent acceptable, or should it be clamped (for example to the pair's own
   AABB diagonal)? I do not have a benchmark that isolates this.
2. **Scope.** The mismatch is symmetric, so both compound algorithms need the change; fixing one
   side leaves `MycollideTT` dropping pairs the leaf callback would have kept.
3. **Upstream Bullet.** If the zero padding is intentional, documenting the band and offering an
   application-level request for a wider tolerance would also work. Either way the dispatcher and
   the compound layer should agree.

## Environment

- Reproduced on the stock Libbulletjme 23.1.0 release binary (DpMt, Windows x64).
- The relevant native files — both compound algorithms, `btCollisionDispatcher`, `btManifoldResult`,
  `btCollisionShape`, and `btPersistentManifold` — are unchanged between 23.1.0, 23.1.2, 23.2.0, and
  current `master` (`git diff` is empty). That makes the same behaviour likely, but it is not a
  substitute for a 23.2.0 run; the 23.1.0 numbers above are the runtime evidence.
- The patch is built from commit `78b76008`, whose library self-reports 23.1.2, so the two are
  different commits rather than a before/after of one build.
- The numbers above come from the reproducer in this report and from a JUnit test that runs the
  same sweep.

## Current workaround

We build Libbulletjme from source with the patch. On our vehicle the symptom was a wheel that lost
an entire 10 ms substep of support whenever its compound separated from the terrain, so the
application saw a step with no contact followed by a step with an unusually large impulse. Filtering
those at the application layer suppresses the symptom but does not restore the support, which is why
I am reporting the engine-side behaviour rather than a workaround.