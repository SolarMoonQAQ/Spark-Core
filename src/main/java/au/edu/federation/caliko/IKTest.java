package au.edu.federation.caliko;

import au.edu.federation.utils.Vec3f;

public class IKTest {

    public static void main(String[] args) {
        System.out.println("=== FABRIK 3D IK Solver Test ===\n");

        // 1. Create a chain with 3 bones
        FabrikChain3D chain = new FabrikChain3D("TestChain");

        // Basebone: start at origin, pointing up Y axis, length 5
        FabrikBone3D basebone = new FabrikBone3D(
                new Vec3f(0, 0, 0),
                new Vec3f(0, 1, 0),
                5.0f,
                "BaseBone"
        );
        chain.addBone(basebone);

        // Consecutive bone 1: continue upward, length 4
        chain.addConsecutiveBone(new Vec3f(0, 1, 0), 4.0f);

        // Consecutive bone 2: continue upward, length 3
        chain.addConsecutiveBone(new Vec3f(0, 1, 0), 3.0f);

        System.out.println("Initial chain configuration:");
        System.out.println("Chain length: " + chain.getChainLength());
        System.out.println("Base location: " + chain.getBaseLocation());
        System.out.println("Effector location: " + chain.getEffectorLocation());
        System.out.println(chain);

        // 2. Solve for a target to the right
        Vec3f target = new Vec3f(6, 4, 0);
        System.out.println("=== Solving for target: " + target + " ===\n");
        float distance = chain.solveForTarget(target);

        System.out.println("After solving:");
        System.out.println("Solve distance: " + distance);
        System.out.println("Base location: " + chain.getBaseLocation());
        System.out.println("Effector location: " + chain.getEffectorLocation());
        for (int i = 0; i < chain.getNumBones(); i++) {
            FabrikBone3D bone = chain.getBone(i);
            System.out.println("Bone " + i + ": start=" + bone.getStartLocation()
                    + " end=" + bone.getEndLocation()
                    + " dir=" + bone.getDirectionUV());
        }

        // 3. Test with a rotor-constrained basebone
        System.out.println("\n=== Test 2: Rotor-constrained basebone ===\n");
        FabrikChain3D chain2 = new FabrikChain3D("ConstrainedChain");

        FabrikBone3D bone0 = new FabrikBone3D(
                new Vec3f(0, 0, 0),
                new Vec3f(0, 1, 0),
                5.0f
        );
        chain2.addBone(bone0);
        chain2.addConsecutiveBone(new Vec3f(0, 1, 0), 4.0f);
        chain2.addConsecutiveBone(new Vec3f(0, 1, 0), 3.0f);

        // Constrain basebone to 45 degrees about global Y axis
        chain2.setRotorBaseboneConstraint(
                FabrikChain3D.BaseboneConstraintType3D.GLOBAL_ROTOR,
                new Vec3f(0, 1, 0),
                45.0f
        );

        Vec3f target2 = new Vec3f(8, 2, 0);
        float dist2 = chain2.solveForTarget(target2);
        System.out.println("Solve distance: " + dist2);
        for (int i = 0; i < chain2.getNumBones(); i++) {
            FabrikBone3D bone = chain2.getBone(i);
            System.out.println("Bone " + i + ": start=" + bone.getStartLocation()
                    + " end=" + bone.getEndLocation());
        }

        // 4. Test hinge-constrained bones
        System.out.println("\n=== Test 3: Hinge-constrained chain ===\n");
        FabrikChain3D chain3 = new FabrikChain3D("HingeChain");

        FabrikBone3D hBone0 = new FabrikBone3D(
                new Vec3f(0, 0, 0),
                new Vec3f(1, 1, 0),
                4.0f
        );
        chain3.addBone(hBone0);

        // Add a global hinge bone that rotates about Z axis
        chain3.addConsecutiveHingedBone(
                new Vec3f(1, 1, 0),   // direction
                3.0f,                  // length
                FabrikJoint3D.JointType.GLOBAL_HINGE, // joint type
                new Vec3f(0, 0, 1),   // hinge rotation axis (Z)
                90.0f,                 // clockwise constraint degs
                90.0f,                 // anticlockwise constraint degs
                new Vec3f(1, 0, 0)    // reference axis
        );

        chain3.addConsecutiveBone(new Vec3f(1, 1, 0), 3.0f);

        Vec3f target3 = new Vec3f(6, 6, 0);
        float dist3 = chain3.solveForTarget(target3);
        System.out.println("Solve distance: " + dist3);
        for (int i = 0; i < chain3.getNumBones(); i++) {
            FabrikBone3D bone = chain3.getBone(i);
            System.out.println("Bone " + i + ": start=" + bone.getStartLocation()
                    + " end=" + bone.getEndLocation());
        }

        // 5. Test multi-chain structure
        System.out.println("\n=== Test 4: Multi-chain structure ===\n");
        FabrikStructure3D structure = new FabrikStructure3D("TestStructure");
        structure.addChain(chain);

        FabrikChain3D chainB = new FabrikChain3D("ChainB");
        chainB.addBone(new FabrikBone3D(new Vec3f(0, 0, 0), new Vec3f(1, 0, 0), 5.0f));
        chainB.addConsecutiveBone(new Vec3f(1, 0, 0), 4.0f);
        chainB.addConsecutiveBone(new Vec3f(1, 0, 0), 3.0f);
        structure.addChain(chainB);

        Vec3f target4 = new Vec3f(5, 5, 5);
        structure.solveForTarget(target4);
        System.out.println("Structure solved for target: " + target4);
        for (int i = 0; i < structure.getNumChains(); i++) {
            FabrikChain3D c = structure.getChain(i);
            System.out.println("Chain " + i + " (" + c.getName() + "): effector=" + c.getEffectorLocation());
        }

        System.out.println("\n=== All tests completed ===");
    }
}
