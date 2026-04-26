package au.edu.federation.caliko;

import au.edu.federation.caliko.FabrikChain3D.BaseboneConstraintType3D;
import au.edu.federation.caliko.FabrikJoint3D.JointType;
import au.edu.federation.utils.Utils;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FabrikChain3D implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private static final String NEW_LINE = System.lineSeparator();
	
	public enum BaseboneConstraintType3D
	{
		NONE,
		GLOBAL_ROTOR,
		LOCAL_ROTOR,
		GLOBAL_HINGE,
		LOCAL_HINGE
	}
	
	private List<FabrikBone3D> mChain = new ArrayList<>();
	private String mName;
	private float mSolveDistanceThreshold = 1.0f;
	private int mMaxIterationAttempts  = 20;
	private float mMinIterationChange = 0.01f;
	private float mChainLength;
	private Vector3f mFixedBaseLocation = new Vector3f();
	private boolean mFixedBaseMode = true;
	private BaseboneConstraintType3D mBaseboneConstraintType = BaseboneConstraintType3D.NONE;
	private Vector3f mBaseboneConstraintUV = new Vector3f();
	private Vector3f mBaseboneRelativeConstraintUV = new Vector3f();
	private Vector3f mBaseboneRelativeReferenceConstraintUV = new Vector3f();
	private Vector3f mLastTargetLocation = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
	private Vector3f mLastBaseLocation = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
	private float mCurrentSolveDistance = Float.MAX_VALUE;
	private int mConnectedChainNumber = -1;
	private int mConnectedBoneNumber  = -1;
	private Vector3f mEmbeddedTarget = new Vector3f();
	private boolean mUseEmbeddedTarget = false;

	public FabrikChain3D() { }
	
	public FabrikChain3D(FabrikChain3D source)
	{
		mChain = source.cloneIkChain();
		
		mFixedBaseLocation.set( source.getBaseLocation() );
		mLastTargetLocation.set(source.mLastTargetLocation);
		mLastBaseLocation.set(source.mLastBaseLocation);
		mEmbeddedTarget.set(source.mEmbeddedTarget);
				
		if (source.mBaseboneConstraintType != BaseboneConstraintType3D.NONE)
		{
			mBaseboneConstraintUV.set(source.mBaseboneConstraintUV);
			mBaseboneRelativeConstraintUV.set(source.mBaseboneRelativeConstraintUV);
		}		
		
		mChainLength            = source.mChainLength;
		mCurrentSolveDistance   = source.mCurrentSolveDistance;
		mConnectedChainNumber   = source.mConnectedChainNumber;
		mConnectedBoneNumber    = source.mConnectedBoneNumber;
		mBaseboneConstraintType = source.mBaseboneConstraintType;			
		mName                   = source.mName;
		mUseEmbeddedTarget      = source.mUseEmbeddedTarget;
	}
	
	public FabrikChain3D(String name) { mName = name; }

	public void addBone(FabrikBone3D bone)
	{
		mChain.add(bone);

		if (mChain.size() == 1)
		{
			mFixedBaseLocation.set( bone.getStartLocation() );
			mBaseboneConstraintUV = bone.getDirectionUV();
		}
		
		updateChainLength();
	}

	public void addConsecutiveBone(Vector3f directionUV, float length)
	{
		Utils.validateDirectionUV(directionUV);
		Utils.validateLength(length);

		if (!mChain.isEmpty())
		{
			Vector3f prevBoneEnd = mChain.get(mChain.size()-1).getEndLocation();
			addBone( new FabrikBone3D(prevBoneEnd, new Vector3f(directionUV).normalize(), length) );
		}
		else
		{
			throw new RuntimeException("You cannot add the basebone as a consecutive bone as it does not provide a start location. Use the addBone() method instead.");
		}
	}
	
	public void addConsecutiveBone(FabrikBone3D bone)
	{
		Vector3f dir = bone.getDirectionUV();
		Utils.validateDirectionUV(dir);
		
		float len = bone.liveLength();
		Utils.validateLength(len);
			
		if (!this.mChain.isEmpty())
		{		
			Vector3f prevBoneEnd = mChain.get(this.mChain.size()-1).getEndLocation();
						
			bone.setStartLocation(prevBoneEnd);
			bone.setEndLocation( prevBoneEnd.add(dir.mul(len, new Vector3f()), new Vector3f()) );
					
			addBone(bone);
		}
		else
		{
			throw new RuntimeException("You cannot add the base bone to a chain using this method as it does not provide a start location.");
		}		
	}

	public void addConsecutiveFreelyRotatingHingedBone(Vector3f directionUV, float length, JointType jointType, Vector3f hingeRotationAxis)
	{
		addConsecutiveHingedBone(directionUV, length, jointType, hingeRotationAxis, 180.0f, 180.0f, Utils.genPerpendicularVectorQuick(hingeRotationAxis, new Vector3f()));
	}
	
	public void addConsecutiveHingedBone(Vector3f directionUV,
			                                       float length,
			                                       JointType jointType,
			                                       Vector3f hingeRotationAxis,
			                                       float clockwiseDegs,
			                                       float anticlockwiseDegs,
			                                       Vector3f hingeConstraintReferenceAxis)
	{
		Utils.validateDirectionUV(directionUV);
		Utils.validateDirectionUV(hingeRotationAxis);
		Utils.validateLength(length);

		if (mChain.isEmpty()) {
		  throw new RuntimeException("You must add a basebone before adding a consectutive bone.");
		}

		directionUV.normalize();
		hingeRotationAxis.normalize();

		Vector3f prevBoneEnd = mChain.get(mChain.size()-1).getEndLocation();
		FabrikBone3D bone = new FabrikBone3D(prevBoneEnd, directionUV, length);
		FabrikJoint3D joint = new FabrikJoint3D();
		switch (jointType)
		{
			case GLOBAL_HINGE:
				joint.setAsGlobalHinge(hingeRotationAxis, clockwiseDegs, anticlockwiseDegs, hingeConstraintReferenceAxis);
				break;
			case LOCAL_HINGE:
				joint.setAsLocalHinge(hingeRotationAxis, clockwiseDegs, anticlockwiseDegs, hingeConstraintReferenceAxis);
				break;
			default:
				throw new IllegalArgumentException("Hinge joint types may be only JointType.GLOBAL_HINGE or JointType.LOCAL_HINGE.");
		}
		bone.setJoint(joint);
		addBone(bone);
	}
	
	public void addConsecutiveRotorConstrainedBone(Vector3f boneDirectionUV, float boneLength, float constraintAngleDegs)
	{
		Utils.validateDirectionUV(boneDirectionUV);
		Utils.validateLength(boneLength);
		if (mChain.isEmpty()) {
		  throw new RuntimeException("Add a basebone before attempting to add consectuive bones.");
		}

		FabrikBone3D bone = new FabrikBone3D(mChain.get(mChain.size()-1).getEndLocation(), new Vector3f(boneDirectionUV).normalize(), boneLength);
		bone.setBallJointConstraintDegs(constraintAngleDegs);
		addBone(bone);
	}	
	
	public Vector3f getBaseboneRelativeConstraintUV() { return mBaseboneRelativeConstraintUV; }
	
	public BaseboneConstraintType3D getBaseboneConstraintType() { return mBaseboneConstraintType; }
	
	public Vector3f getBaseboneConstraintUV()
	{
		if ( mBaseboneConstraintType != BaseboneConstraintType3D.NONE )
		{
			return mBaseboneConstraintUV;
		}
		else
		{
			throw new RuntimeException("Cannot return the basebone constraint when the basebone constraint type is NONE.");
		}
	}
	
	public Vector3f getBaseLocation() { return mChain.get(0).getStartLocation(); }	
	
	public FabrikBone3D getBone(int boneNumber) { return mChain.get(boneNumber); }

	public List<FabrikBone3D> getChain() { return mChain; }
	
	public float getChainLength() { return mChainLength; }
	
	public int getConnectedBoneNumber() { return mConnectedBoneNumber; }

	public int getConnectedChainNumber() { return mConnectedChainNumber; }
	
	public Vector3f getEffectorLocation() { return mChain.get(mChain.size()-1).getEndLocation(); }

	public boolean getEmbeddedTargetMode() { return mUseEmbeddedTarget; }
	
	public Vector3f getEmbeddedTarget() { return mEmbeddedTarget; }
	
	public Vector3f getLastTargetLocation() { return mLastTargetLocation; }
	
	public float getLiveChainLength()
	{
		float length = 0.0f;		
		for (FabrikBone3D aBone : this.mChain)
		{  
			length += aBone.liveLength();
		}		
		return length;
	}	
	
	public String getName() { return mName; }
	
	public int getNumBones() { return mChain.size(); }
	
	public void removeBone(int boneNumber)
	{
		if (boneNumber < mChain.size())
		{	
			mChain.remove(boneNumber);
			updateChainLength();
		}
		else
		{
			throw new IllegalArgumentException("Bone " + boneNumber + " does not exist to be removed from the chain. Bones are zero indexed.");
		}
	}
	
	void setBaseboneRelativeConstraintUV(Vector3f constraintUV) { mBaseboneRelativeConstraintUV = constraintUV; }

	void setBaseboneRelativeReferenceConstraintUV(Vector3f constraintUV) { mBaseboneRelativeReferenceConstraintUV = constraintUV; }
	
	public Vector3f getBaseboneRelativeReferenceConstraintUV()	{ return mBaseboneRelativeReferenceConstraintUV;}
	
	public void setEmbeddedTargetMode(boolean value) { mUseEmbeddedTarget = value; }
	
	public void setRotorBaseboneConstraint(BaseboneConstraintType3D rotorType, Vector3f constraintAxis, float angleDegs)
	{
		if (mChain.isEmpty())	{ 
		  throw new RuntimeException("Chain must contain a basebone before we can specify the basebone constraint type."); 
		}		
		if ( constraintAxis.length() <= 0.0f ) { 
		  throw new IllegalArgumentException("Constraint axis cannot be zero."); 
		}
		if (angleDegs < 0.0f ) { 
		  angleDegs = 0.0f; 
		}
		if (angleDegs > 180.0f) { 
		  angleDegs = 180.0f;
		}		
		if ( !(rotorType == BaseboneConstraintType3D.GLOBAL_ROTOR || rotorType == BaseboneConstraintType3D.LOCAL_ROTOR) )
		{
			throw new IllegalArgumentException("The only valid rotor types for this method are GLOBAL_ROTOR and LOCAL_ROTOR.");
		}
				
		mBaseboneConstraintType = rotorType;
		mBaseboneConstraintUV.set(new Vector3f(constraintAxis).normalize());
		mBaseboneRelativeConstraintUV.set(mBaseboneConstraintUV);
		getBone(0).getJoint().setAsBallJoint(angleDegs);
	}	
	
	public void setHingeBaseboneConstraint(BaseboneConstraintType3D hingeType, Vector3f hingeRotationAxis, float cwConstraintDegs, float acwConstraintDegs, Vector3f hingeReferenceAxis)
	{
		if (mChain.isEmpty())	{ 
		  throw new RuntimeException("Chain must contain a basebone before we can specify the basebone constraint type."); 
		}		
		if ( hingeRotationAxis.length() <= 0.0f )  { 
		  throw new IllegalArgumentException("Hinge rotation axis cannot be zero.");
		}
		if ( hingeReferenceAxis.length() <= 0.0f ) { 
		  throw new IllegalArgumentException("Hinge reference axis cannot be zero.");	
		}
		if ( !Utils.approximatelyEquals(Utils.dotProduct(hingeRotationAxis, hingeReferenceAxis), 0.0f, 0.01f) ) {
			throw new IllegalArgumentException("The hinge reference axis must be in the plane of the hinge rotation axis, that is, they must be perpendicular.");
		}
		if ( !(hingeType == BaseboneConstraintType3D.GLOBAL_HINGE || hingeType == BaseboneConstraintType3D.LOCAL_HINGE) ) {	
			throw new IllegalArgumentException("The only valid hinge types for this method are GLOBAL_HINGE and LOCAL_HINGE.");
		}
		
		mBaseboneConstraintType = hingeType;
		mBaseboneConstraintUV.set( new Vector3f(hingeRotationAxis).normalize() );
		
		FabrikJoint3D hinge = new FabrikJoint3D();
		
		if (hingeType == BaseboneConstraintType3D.GLOBAL_HINGE)
		{
			hinge.setHinge(JointType.GLOBAL_HINGE, hingeRotationAxis, cwConstraintDegs, acwConstraintDegs, hingeReferenceAxis);
		}
		else
		{
			hinge.setHinge(JointType.LOCAL_HINGE, hingeRotationAxis, cwConstraintDegs, acwConstraintDegs, hingeReferenceAxis);
		}
		getBone(0).setJoint(hinge);
	}
	
	public void setFreelyRotatingGlobalHingedBasebone(Vector3f hingeRotationAxis)
	{
		setHingeBaseboneConstraint(BaseboneConstraintType3D.GLOBAL_HINGE, hingeRotationAxis, 180.0f, 180.0f, Utils.genPerpendicularVectorQuick(hingeRotationAxis, new Vector3f()) );
	}
	
	public void setFreelyRotatingLocalHingedBasebone(Vector3f hingeRotationAxis)
	{
		setHingeBaseboneConstraint(BaseboneConstraintType3D.LOCAL_HINGE, hingeRotationAxis, 180.0f, 180.0f, Utils.genPerpendicularVectorQuick(hingeRotationAxis, new Vector3f()) );
	}
	
	public void setLocalHingedBasebone(Vector3f hingeRotationAxis, float cwDegs, float acwDegs, Vector3f hingeReferenceAxis)
	{
		setHingeBaseboneConstraint(BaseboneConstraintType3D.LOCAL_HINGE, hingeRotationAxis, cwDegs, acwDegs, hingeReferenceAxis);
	}
	
	public void setGlobalHingedBasebone(Vector3f hingeRotationAxis, float cwDegs, float acwDegs, Vector3f hingeReferenceAxis)
	{
		setHingeBaseboneConstraint(BaseboneConstraintType3D.GLOBAL_HINGE, hingeRotationAxis, cwDegs, acwDegs, hingeReferenceAxis);
	}
	
	public void setBaseboneConstraintUV(Vector3f constraintUV)
	{
		if (mBaseboneConstraintType == BaseboneConstraintType3D.NONE)
		{
			throw new IllegalArgumentException("Specify the basebone constraint type with setBaseboneConstraintTypeCannot specify a basebone constraint when the current constraint type is BaseboneConstraint.NONE.");
		}
		
		Utils.validateDirectionUV(constraintUV);
		
		constraintUV.normalize();
		mBaseboneConstraintUV.set(constraintUV);
	}

	public void setBaseLocation(Vector3f baseLocation) { mFixedBaseLocation = baseLocation; }

	public void connectToStructure(FabrikStructure3D structure, int chainNumber, int boneNumber)
	{
		int numChains = structure.getNumChains();
		if (chainNumber > numChains) { 
		  throw new IllegalArgumentException("Structure does not contain a chain " + chainNumber + " - it has " + numChains + " chains."); 
		}
		
		int numBones = structure.getChain(chainNumber).getNumBones();
		if (boneNumber > numBones) { 
		  throw new IllegalArgumentException("Chain does not contain a bone " + boneNumber + " - it has " + numBones + " bones."); 
		}
		
		mConnectedChainNumber = chainNumber;
		mConnectedBoneNumber  = boneNumber;		
	}
	
	public void setFixedBaseMode(boolean value)
	{	
		if (!value && mConnectedChainNumber != -1)
		{
			throw new RuntimeException("This chain is connected to another chain so must remain in fixed base mode.");
		}
		
		if (mBaseboneConstraintType == BaseboneConstraintType3D.GLOBAL_ROTOR && !value)
		{
			throw new RuntimeException("Cannot set a non-fixed base mode when the chain's constraint type is BaseboneConstraintType3D.GLOBAL_ABSOLUTE_ROTOR.");
		}
		
		mFixedBaseMode = value;
	}
	
	public void setMaxIterationAttempts(int maxIterations)
	{
		if (maxIterations < 1)
		{
			throw new IllegalArgumentException("The maximum number of attempts to solve this IK chain must be at least 1.");
		}
		
		mMaxIterationAttempts = maxIterations;
	}

	public void setMinIterationChange(float minIterationChange)
	{
		if (minIterationChange < 0.0f)
		{
			throw new IllegalArgumentException("The minimum iteration change cannot be less than zero.");
		}
		
		mMinIterationChange = minIterationChange;
	}

	public void setSolveDistanceThreshold(float solveDistanceThreshold)
	{
		if (solveDistanceThreshold < 0.0f)
		{
			throw new IllegalArgumentException("Solve distance threshold must be greater than or equal to zero.");
		}
		
		mSolveDistanceThreshold = solveDistanceThreshold;
	}

	public void setName(String name) { mName = Utils.getValidatedName(name); }
	
	public float solveForTarget(float targetX, float targetY, float targetZ)
	{
		return solveForTarget( new Vector3f(targetX, targetY, targetZ) );
	}

	public float solveForTarget(Vector3f newTarget)
	{
		boolean mustSolve = false;
		
		Vector3f effectorLocation = getEffectorLocation();
		mCurrentSolveDistance = effectorLocation.distance(newTarget);
		
		if ( !Utils.approximatelyEquals(mCurrentSolveDistance, 0.0f, mSolveDistanceThreshold) )
		{
			mustSolve = true;
		}
		
		if ( !mLastBaseLocation.equals( getBaseLocation() ) )
		{
			mustSolve = true;
		}
		
		if (!mLastTargetLocation.equals(newTarget))
		{
			mustSolve = true;
		}
		
		if (mustSolve)
		{
			mLastTargetLocation.set(newTarget);
			mLastBaseLocation.set( getBaseLocation() );
			
			mCurrentSolveDistance = solveIK(newTarget);
		}
		
		return mCurrentSolveDistance;
	}
	
	public void solveForEmbeddedTarget()
	{
		solveForTarget(mEmbeddedTarget);
	}

	public void updateEmbeddedTarget(Vector3f newEmbeddedTarget)
	{
		mEmbeddedTarget.set(newEmbeddedTarget);
	}

	public void updateEmbeddedTarget(float x, float y, float z)
	{
		mEmbeddedTarget.set( new Vector3f(x, y, z) );
	}
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append("--- FabrikChain3D ---" + NEW_LINE);
		sb.append("Name: " + mName + NEW_LINE);
		sb.append("Chain Length: " + mChainLength + NEW_LINE);
		sb.append("Base location: " + getBaseLocation() + NEW_LINE);
		sb.append("Effector location: " + getEffectorLocation() + NEW_LINE);
		sb.append("Last target location: " + mLastTargetLocation + NEW_LINE);
		sb.append("Fixed base mode: " + mFixedBaseMode + NEW_LINE);
		sb.append("Basebone constraint type: " + mBaseboneConstraintType + NEW_LINE);
		sb.append("Number of bones: " + mChain.size() + NEW_LINE);
		
		for (int i = 0; i < mChain.size(); ++i)
		{
			sb.append("Bone " + i + ":" + NEW_LINE);
			sb.append( mChain.get(i) + NEW_LINE);
		}
		
		return sb.toString();
	}

	private float solveIK(Vector3f target)
	{
		Vector3f bestSolutionStartLocation = new Vector3f();
		Vector3f bestSolutionEndLocation = new Vector3f();
		
		int numBones = mChain.size();
		boolean solutionAccepted = false;
		int iterationCount = 0;
		float distanceToTarget = Float.MAX_VALUE;
		float lastDistanceToTarget = Float.MAX_VALUE;
		float distanceMet = 0.0f;	
		
		Vector3f[] fwdPassBeginLocations = new Vector3f[numBones];
		Vector3f[] fwdPassEndLocations   = new Vector3f[numBones];
		for (int loop = 0; loop < numBones; ++loop)
		{
			fwdPassBeginLocations[loop] = new Vector3f();
			fwdPassEndLocations[loop]   = new Vector3f();
		}
		
		Vector3f[] backupBoneStartLocations = new Vector3f[numBones];
		Vector3f[] backupBoneEndLocations   = new Vector3f[numBones];
		for (int loop = 0; loop < numBones; ++loop)
		{
			backupBoneStartLocations[loop] = new Vector3f();
			backupBoneEndLocations[loop]   = new Vector3f();
		}
		
		while (!solutionAccepted)
		{
			++iterationCount;
			
			if (iterationCount > 1)
			{
				for (int loop = 0; loop < numBones; ++loop)
				{
					backupBoneStartLocations[loop].set( mChain.get(loop).getStartLocation() );
					backupBoneEndLocations[loop].set( mChain.get(loop).getEndLocation() );
				}
			}
			
			// ---------- Forward pass from end effector to base ----------
			for (int loop = (numBones - 1); loop >= 0; --loop)
			{
				FabrikBone3D thisBone = mChain.get(loop);
				float thisBoneLength  = thisBone.length();
				FabrikJoint3D thisBoneJoint = thisBone.getJoint();
				JointType thisBoneJointType = thisBone.getJointType();

				if (loop != mChain.size() - 1)
				{
					Vector3f outerBoneOuterToInnerUV = new Vector3f(mChain.get(loop+1).getDirectionUV()).negate();

					Vector3f thisBoneOuterToInnerUV = new Vector3f(thisBone.getDirectionUV()).negate();
					
					if (thisBoneJointType == JointType.BALL)
					{	
						float angleBetweenDegs    = (float)Math.toDegrees(outerBoneOuterToInnerUV.angle(thisBoneOuterToInnerUV));
						float constraintAngleDegs = thisBoneJoint.getBallJointConstraintDegs();
						if (angleBetweenDegs > constraintAngleDegs)
						{	
							thisBoneOuterToInnerUV = Utils.angleLimitedUnitVectorDegs(thisBoneOuterToInnerUV, outerBoneOuterToInnerUV, constraintAngleDegs, new Vector3f());
						}
					}
					else if (thisBoneJointType == JointType.GLOBAL_HINGE)
					{	
						thisBoneOuterToInnerUV = Utils.projectOntoPlane(thisBoneOuterToInnerUV, thisBoneJoint.getHingeRotationAxis(), new Vector3f()); 
					}
					else if (thisBoneJointType == JointType.LOCAL_HINGE)
					{	
						Vector3f relativeHingeRotationAxis;
						if (loop > 0) {
							Matrix3f m = Utils.createRotationMatrix( mChain.get(loop-1).getDirectionUV(), new Matrix3f());
							relativeHingeRotationAxis = m.transform(thisBoneJoint.getHingeRotationAxis(), new Vector3f()).normalize();
						}
						else
						{
							relativeHingeRotationAxis = mBaseboneRelativeConstraintUV;
						}
						
						thisBoneOuterToInnerUV = Utils.projectOntoPlane(thisBoneOuterToInnerUV, relativeHingeRotationAxis, new Vector3f());
					}
						
					Vector3f newStartLocation = thisBone.getEndLocation().add(thisBoneOuterToInnerUV.mul(thisBoneLength, new Vector3f()), new Vector3f());

					thisBone.setStartLocation(newStartLocation);

					if (loop > 0)
					{
						mChain.get(loop-1).setEndLocation(newStartLocation);
					}
				}
				else
				{
					thisBone.setEndLocation(target);
					
					Vector3f thisBoneOuterToInnerUV = new Vector3f(thisBone.getDirectionUV()).negate();
					
					switch ( thisBoneJointType )
					{
						case BALL:
							break;						
						case GLOBAL_HINGE:
							thisBoneOuterToInnerUV = Utils.projectOntoPlane(thisBoneOuterToInnerUV, thisBoneJoint.getHingeRotationAxis(), new Vector3f());
							break;
						case LOCAL_HINGE:
							Matrix3f m = Utils.createRotationMatrix( mChain.get(loop-1).getDirectionUV(), new Matrix3f() );
							Vector3f relativeHingeRotationAxis = m.transform(thisBoneJoint.getHingeRotationAxis(), new Vector3f()).normalize();
							thisBoneOuterToInnerUV = Utils.projectOntoPlane(thisBoneOuterToInnerUV, relativeHingeRotationAxis, new Vector3f());
							break;
					}
											
					Vector3f newStartLocation = target.add(thisBoneOuterToInnerUV.mul(thisBoneLength, new Vector3f()), new Vector3f());
					
					thisBone.setStartLocation(newStartLocation);

					if (loop > 0)
					{
						mChain.get(loop-1).setEndLocation(newStartLocation);
					}
				}
				
			}

			// ---------- Backward pass from base to end effector -----------
 
			for (int loop = 0; loop < mChain.size(); ++loop)
			{
				FabrikBone3D thisBone = mChain.get(loop);
				float thisBoneLength  = thisBone.length();

				if (loop != 0)
				{
					Vector3f thisBoneInnerToOuterUV = thisBone.getDirectionUV();
					Vector3f prevBoneInnerToOuterUV = mChain.get(loop-1).getDirectionUV();
					
					FabrikJoint3D thisBoneJoint = thisBone.getJoint();
					JointType jointType = thisBoneJoint.getJointType();
					if (jointType == JointType.BALL)
					{					
						float angleBetweenDegs    = (float)Math.toDegrees(prevBoneInnerToOuterUV.angle(thisBoneInnerToOuterUV));
						float constraintAngleDegs = thisBoneJoint.getBallJointConstraintDegs(); 
						
						if (angleBetweenDegs > constraintAngleDegs)
						{
							thisBoneInnerToOuterUV = Utils.angleLimitedUnitVectorDegs(thisBoneInnerToOuterUV, prevBoneInnerToOuterUV, constraintAngleDegs, new Vector3f());
						}
					}
					else if (jointType == JointType.GLOBAL_HINGE)
					{					
						Vector3f hingeRotationAxis  =  thisBoneJoint.getHingeRotationAxis();
						thisBoneInnerToOuterUV = Utils.projectOntoPlane(thisBoneInnerToOuterUV, hingeRotationAxis, new Vector3f());
						
						float cwConstraintDegs   = -thisBoneJoint.getHingeClockwiseConstraintDegs();
						float acwConstraintDegs  =  thisBoneJoint.getHingeAnticlockwiseConstraintDegs();
						if ( !( Utils.approximatelyEquals(cwConstraintDegs, -FabrikJoint3D.MAX_CONSTRAINT_ANGLE_DEGS, 0.001f) ) &&
							 !( Utils.approximatelyEquals(acwConstraintDegs, FabrikJoint3D.MAX_CONSTRAINT_ANGLE_DEGS, 0.001f) ) )
						{
							Vector3f hingeReferenceAxis =  thisBoneJoint.getHingeReferenceAxis();
							
							float signedAngleDegs = Utils.getSignedAngleBetweenDegs(hingeReferenceAxis, thisBoneInnerToOuterUV, hingeRotationAxis);
							
				        	if (signedAngleDegs > acwConstraintDegs)
				        	{	
				        		thisBoneInnerToOuterUV = new Vector3f(hingeReferenceAxis).rotateAxis((float)Math.toRadians(acwConstraintDegs), hingeRotationAxis.x, hingeRotationAxis.y, hingeRotationAxis.z).normalize(new Vector3f());		        		
				        	}
				        	else if (signedAngleDegs < cwConstraintDegs)
				        	{	
				        		thisBoneInnerToOuterUV = new Vector3f(hingeReferenceAxis).rotateAxis((float)Math.toRadians(cwConstraintDegs), hingeRotationAxis.x, hingeRotationAxis.y, hingeRotationAxis.z).normalize(new Vector3f());			        		
				        	}
						}
					}
					else if (jointType == JointType.LOCAL_HINGE)
					{	
						Vector3f hingeRotationAxis  = thisBoneJoint.getHingeRotationAxis();
						
						Matrix3f m = Utils.createRotationMatrix(prevBoneInnerToOuterUV, new Matrix3f());
						
						Vector3f relativeHingeRotationAxis  = m.transform(hingeRotationAxis, new Vector3f()).normalize();
						
						thisBoneInnerToOuterUV = Utils.projectOntoPlane(thisBoneInnerToOuterUV, relativeHingeRotationAxis, new Vector3f());
						
						float cwConstraintDegs   = -thisBoneJoint.getHingeClockwiseConstraintDegs();
						float acwConstraintDegs  =  thisBoneJoint.getHingeAnticlockwiseConstraintDegs();
						if ( !( Utils.approximatelyEquals(cwConstraintDegs, -FabrikJoint3D.MAX_CONSTRAINT_ANGLE_DEGS, 0.001f) ) &&
							 !( Utils.approximatelyEquals(acwConstraintDegs, FabrikJoint3D.MAX_CONSTRAINT_ANGLE_DEGS, 0.001f) ) )
						{
							Vector3f relativeHingeReferenceAxis = m.transform( thisBoneJoint.getHingeReferenceAxis(), new Vector3f()).normalize();
							
							float signedAngleDegs = Utils.getSignedAngleBetweenDegs(relativeHingeReferenceAxis, thisBoneInnerToOuterUV, relativeHingeRotationAxis);
							
				        	if (signedAngleDegs > acwConstraintDegs)
				        	{	
				        		thisBoneInnerToOuterUV = new Vector3f(relativeHingeReferenceAxis).rotateAxis((float)Math.toRadians(acwConstraintDegs), relativeHingeRotationAxis.x, relativeHingeRotationAxis.y, relativeHingeRotationAxis.z).normalize(new Vector3f());		        		
				        	}
				        	else if (signedAngleDegs < cwConstraintDegs)
				        	{	
				        		thisBoneInnerToOuterUV = new Vector3f(relativeHingeReferenceAxis).rotateAxis((float)Math.toRadians(cwConstraintDegs), relativeHingeRotationAxis.x, relativeHingeRotationAxis.y, relativeHingeRotationAxis.z).normalize(new Vector3f());			        		
				        	}
						}
						
					}
					
					Vector3f newEndLocation = thisBone.getStartLocation().add(thisBoneInnerToOuterUV.mul(thisBoneLength, new Vector3f()), new Vector3f());

					thisBone.setEndLocation(newEndLocation);

					if (loop < mChain.size() - 1) { 
					  mChain.get(loop+1).setStartLocation(newEndLocation); 
					}
				}
				else
				{	
					if (mFixedBaseMode)
					{
						thisBone.setStartLocation(mFixedBaseLocation);
					}
					else
					{
						thisBone.setStartLocation( thisBone.getEndLocation().sub(thisBone.getDirectionUV().mul(thisBoneLength, new Vector3f()), new Vector3f()) );
					}
					
					if (mBaseboneConstraintType == BaseboneConstraintType3D.NONE)
					{
						Vector3f newEndLocation = thisBone.getStartLocation().add(thisBone.getDirectionUV().mul(thisBoneLength, new Vector3f()), new Vector3f());
						thisBone.setEndLocation(newEndLocation);	
						
						if (mChain.size() > 1) { 
						  mChain.get(1).setStartLocation(newEndLocation); 
						}
					}
					else
					{	
						if (mBaseboneConstraintType == BaseboneConstraintType3D.GLOBAL_ROTOR)
						{	
							Vector3f thisBoneInnerToOuterUV = thisBone.getDirectionUV();
									
							float angleBetweenDegs    = (float)Math.toDegrees(mBaseboneConstraintUV.angle(thisBoneInnerToOuterUV));
							float constraintAngleDegs = thisBone.getBallJointConstraintDegs(); 
						
							if (angleBetweenDegs > constraintAngleDegs)
							{
								thisBoneInnerToOuterUV = Utils.angleLimitedUnitVectorDegs(thisBoneInnerToOuterUV, mBaseboneConstraintUV, constraintAngleDegs, new Vector3f());
							}
							
							Vector3f newEndLocation = thisBone.getStartLocation().add(thisBoneInnerToOuterUV.mul(thisBoneLength, new Vector3f()), new Vector3f());
							
							thisBone.setEndLocation( newEndLocation );
							
							if (mChain.size() > 1) { 
							  mChain.get(1).setStartLocation(newEndLocation); 
							}
						}
						else if (mBaseboneConstraintType == BaseboneConstraintType3D.LOCAL_ROTOR)
						{
							Vector3f thisBoneInnerToOuterUV = thisBone.getDirectionUV();
									
							float angleBetweenDegs    = (float)Math.toDegrees(mBaseboneRelativeConstraintUV.angle(thisBoneInnerToOuterUV));
							float constraintAngleDegs = thisBone.getBallJointConstraintDegs();
							if (angleBetweenDegs > constraintAngleDegs)
							{
								thisBoneInnerToOuterUV = Utils.angleLimitedUnitVectorDegs(thisBoneInnerToOuterUV, mBaseboneRelativeConstraintUV, constraintAngleDegs, new Vector3f());
							}
							
							Vector3f newEndLocation = thisBone.getStartLocation().add(thisBoneInnerToOuterUV.mul(thisBoneLength, new Vector3f()), new Vector3f());						
							thisBone.setEndLocation( newEndLocation );
							
							if (mChain.size() > 1) { 
							  mChain.get(1).setStartLocation(newEndLocation); 
							}
						}
						else if (mBaseboneConstraintType == BaseboneConstraintType3D.GLOBAL_HINGE)
						{
							FabrikJoint3D thisJoint  =  thisBone.getJoint();
							Vector3f hingeRotationAxis  =  thisJoint.getHingeRotationAxis();
							float cwConstraintDegs   = -thisJoint.getHingeClockwiseConstraintDegs();
							float acwConstraintDegs  =  thisJoint.getHingeAnticlockwiseConstraintDegs();
							
							Vector3f thisBoneInnerToOuterUV = Utils.projectOntoPlane(thisBone.getDirectionUV(), hingeRotationAxis, new Vector3f());
									
							if ( !( Utils.approximatelyEquals(cwConstraintDegs , -FabrikJoint3D.MAX_CONSTRAINT_ANGLE_DEGS, 0.01f) &&
								    Utils.approximatelyEquals(acwConstraintDegs,  FabrikJoint3D.MAX_CONSTRAINT_ANGLE_DEGS, 0.01f) ) )
							{
								Vector3f hingeReferenceAxis = thisJoint.getHingeReferenceAxis();
								float signedAngleDegs    = Utils.getSignedAngleBetweenDegs(hingeReferenceAxis, thisBoneInnerToOuterUV, hingeRotationAxis);
								
					        	if (signedAngleDegs > acwConstraintDegs)
					        	{	
					        		thisBoneInnerToOuterUV = new Vector3f(hingeReferenceAxis).rotateAxis((float)Math.toRadians(acwConstraintDegs), hingeRotationAxis.x, hingeRotationAxis.y, hingeRotationAxis.z).normalize(new Vector3f());		        		
					        	}
					        	else if (signedAngleDegs < cwConstraintDegs)
					        	{	
					        		thisBoneInnerToOuterUV = new Vector3f(hingeReferenceAxis).rotateAxis((float)Math.toRadians(cwConstraintDegs), hingeRotationAxis.x, hingeRotationAxis.y, hingeRotationAxis.z).normalize(new Vector3f());			        		
					        	}
							}
							
							Vector3f newEndLocation = thisBone.getStartLocation().add(thisBoneInnerToOuterUV.mul(thisBoneLength, new Vector3f()), new Vector3f());						
							thisBone.setEndLocation( newEndLocation );
							
							if (mChain.size() > 1) { 
							  mChain.get(1).setStartLocation(newEndLocation); 
							}
						}
						else if (mBaseboneConstraintType == BaseboneConstraintType3D.LOCAL_HINGE)
						{
							FabrikJoint3D thisJoint  =  thisBone.getJoint();
							Vector3f hingeRotationAxis  =  mBaseboneRelativeConstraintUV;
							float cwConstraintDegs   = -thisJoint.getHingeClockwiseConstraintDegs();
							float acwConstraintDegs  =  thisJoint.getHingeAnticlockwiseConstraintDegs();
							
							Vector3f thisBoneInnerToOuterUV = Utils.projectOntoPlane(thisBone.getDirectionUV(), hingeRotationAxis, new Vector3f());
							
							if ( !( Utils.approximatelyEquals(cwConstraintDegs , -FabrikJoint3D.MAX_CONSTRAINT_ANGLE_DEGS, 0.01f) &&
								    Utils.approximatelyEquals(acwConstraintDegs,  FabrikJoint3D.MAX_CONSTRAINT_ANGLE_DEGS, 0.01f) ) )
							{
								Vector3f hingeReferenceAxis = mBaseboneRelativeReferenceConstraintUV; 
								float signedAngleDegs    = Utils.getSignedAngleBetweenDegs(hingeReferenceAxis, thisBoneInnerToOuterUV, hingeRotationAxis);
								
					        	if (signedAngleDegs > acwConstraintDegs)
					        	{	
					        		thisBoneInnerToOuterUV = new Vector3f(hingeReferenceAxis).rotateAxis((float)Math.toRadians(acwConstraintDegs), hingeRotationAxis.x, hingeRotationAxis.y, hingeRotationAxis.z).normalize(new Vector3f());		        		
					        	}
					        	else if (signedAngleDegs < cwConstraintDegs)
					        	{	
					        		thisBoneInnerToOuterUV = new Vector3f(hingeReferenceAxis).rotateAxis((float)Math.toRadians(cwConstraintDegs), hingeRotationAxis.x, hingeRotationAxis.y, hingeRotationAxis.z).normalize(new Vector3f());			        		
					        	}
							}
							
							Vector3f newEndLocation = thisBone.getStartLocation().add(thisBoneInnerToOuterUV.mul(thisBoneLength, new Vector3f()), new Vector3f());						
							thisBone.setEndLocation( newEndLocation );
							
							if (mChain.size() > 1) { 
							  mChain.get(1).setStartLocation(newEndLocation); 
							}
						}
						
					}
				}
				
			}

			distanceToTarget = mChain.get(mChain.size()-1).getEndLocation().distance(target);
			
			if (distanceToTarget <= mSolveDistanceThreshold)
			{
				solutionAccepted = true;
			}
			else if (iterationCount >= mMaxIterationAttempts)
			{
				solutionAccepted = true;
			}
			else if (iterationCount > 1 && (lastDistanceToTarget - distanceToTarget) < mMinIterationChange)
			{
				solutionAccepted = true;
				for (int loop = 0; loop < numBones; ++loop)
				{
					mChain.get(loop).setStartLocation(backupBoneStartLocations[loop]);
					mChain.get(loop).setEndLocation(backupBoneEndLocations[loop]);
				}
				distanceToTarget = mChain.get(mChain.size()-1).getEndLocation().distance(target);
			}
			
			lastDistanceToTarget = distanceToTarget;
		}
		
		return distanceToTarget;
	}
	
	private void updateChainLength()
	{
		mChainLength = 0.0f;		
		for (FabrikBone3D aBone : this.mChain)
		{  
			mChainLength += aBone.length();
		}
	}
	
	private List<FabrikBone3D> cloneIkChain()
	{
		List<FabrikBone3D> clonedChain = new ArrayList<>();
		
		for (FabrikBone3D aBone : this.mChain)
		{
			clonedChain.add( new FabrikBone3D( aBone ) );
		}
		
		return clonedChain;
	}
	
	public int getMaxIterationAttempts() {
		return this.mMaxIterationAttempts;
	}
	
	public float getMinIterationChange() {
		return this.mMinIterationChange;
	}
	
	public float getSolveDistanceThreshold() {
		return this.mSolveDistanceThreshold;
	}

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((mBaseboneConstraintType == null) ? 0 : mBaseboneConstraintType.hashCode());
    result = prime * result + ((mBaseboneConstraintUV == null) ? 0 : mBaseboneConstraintUV.hashCode());
    result = prime * result + ((mBaseboneRelativeConstraintUV == null) ? 0 : mBaseboneRelativeConstraintUV.hashCode());
    result = prime * result
        + ((mBaseboneRelativeReferenceConstraintUV == null) ? 0 : mBaseboneRelativeReferenceConstraintUV.hashCode());
    result = prime * result + ((mChain == null) ? 0 : mChain.hashCode());
    result = prime * result + Float.floatToIntBits(mChainLength);
    result = prime * result + mConnectedBoneNumber;
    result = prime * result + mConnectedChainNumber;
    result = prime * result + Float.floatToIntBits(mCurrentSolveDistance);
    result = prime * result + ((mEmbeddedTarget == null) ? 0 : mEmbeddedTarget.hashCode());
    result = prime * result + ((mFixedBaseLocation == null) ? 0 : mFixedBaseLocation.hashCode());
    result = prime * result + (mFixedBaseMode ? 1231 : 1237);
    result = prime * result + ((mLastBaseLocation == null) ? 0 : mLastBaseLocation.hashCode());
    result = prime * result + ((mLastTargetLocation == null) ? 0 : mLastTargetLocation.hashCode());
    result = prime * result + mMaxIterationAttempts;
    result = prime * result + Float.floatToIntBits(mMinIterationChange);
    result = prime * result + ((mName == null) ? 0 : mName.hashCode());
    result = prime * result + Float.floatToIntBits(mSolveDistanceThreshold);
    result = prime * result + (mUseEmbeddedTarget ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    FabrikChain3D other = (FabrikChain3D) obj;
    if (mBaseboneConstraintType != other.mBaseboneConstraintType) {
      return false;
    }
    if (mBaseboneConstraintUV == null) {
      if (other.mBaseboneConstraintUV != null) {
        return false;
      }
    } else if (!mBaseboneConstraintUV.equals(other.mBaseboneConstraintUV)) {
      return false;
    }
    if (mBaseboneRelativeConstraintUV == null) {
      if (other.mBaseboneRelativeConstraintUV != null) {
        return false;
      }
    } else if (!mBaseboneRelativeConstraintUV.equals(other.mBaseboneRelativeConstraintUV)) {
      return false;
    }
    if (mBaseboneRelativeReferenceConstraintUV == null) {
      if (other.mBaseboneRelativeReferenceConstraintUV != null) {
        return false;
      }
    } else if (!mBaseboneRelativeReferenceConstraintUV.equals(other.mBaseboneRelativeReferenceConstraintUV)) {
      return false;
    }
    if (mChain == null) {
      if (other.mChain != null) {
        return false;
      }
    } else if (!mChain.equals(other.mChain)) {
      return false;
    }
    if (Float.floatToIntBits(mChainLength) != Float.floatToIntBits(other.mChainLength)) {
      return false;
    }
    if (mConnectedBoneNumber != other.mConnectedBoneNumber) {
      return false;
    }
    if (mConnectedChainNumber != other.mConnectedChainNumber) {
      return false;
    }
    if (Float.floatToIntBits(mCurrentSolveDistance) != Float.floatToIntBits(other.mCurrentSolveDistance)) {
      return false;
    }
    if (mEmbeddedTarget == null) {
      if (other.mEmbeddedTarget != null) {
        return false;
      }
    } else if (!mEmbeddedTarget.equals(other.mEmbeddedTarget)) {
      return false;
    }
    if (mFixedBaseLocation == null) {
      if (other.mFixedBaseLocation != null) {
        return false;
      }
    } else if (!mFixedBaseLocation.equals(other.mFixedBaseLocation)) {
      return false;
    }
    if (mFixedBaseMode != other.mFixedBaseMode) {
      return false;
    }
    if (mLastBaseLocation == null) {
      if (other.mLastBaseLocation != null) {
        return false;
      }
    } else if (!mLastBaseLocation.equals(other.mLastBaseLocation)) {
      return false;
    }
    if (mLastTargetLocation == null) {
      if (other.mLastTargetLocation != null) {
        return false;
      }
    } else if (!mLastTargetLocation.equals(other.mLastTargetLocation)) {
      return false;
    }
    if (mMaxIterationAttempts != other.mMaxIterationAttempts) {
      return false;
    }
    if (Float.floatToIntBits(mMinIterationChange) != Float.floatToIntBits(other.mMinIterationChange)) {
      return false;
    }
    if (mName == null) {
      if (other.mName != null) {
        return false;
      }
    } else if (!mName.equals(other.mName)) {
      return false;
    }
    if (Float.floatToIntBits(mSolveDistanceThreshold) != Float.floatToIntBits(other.mSolveDistanceThreshold)) {
      return false;
    }
    if (mUseEmbeddedTarget != other.mUseEmbeddedTarget) {
      return false;
    }
    return true;
  }	

}
