package au.edu.federation.caliko;

import au.edu.federation.caliko.FabrikJoint3D.JointType;
import au.edu.federation.utils.Utils;
import org.joml.Vector3f;

import java.io.Serializable;

public class FabrikBone3D implements Serializable
{
	private static final long serialVersionUID = 1L;

	private static final String NEW_LINE = System.lineSeparator();

	private BoneConnectionPoint mBoneConnectionPoint = BoneConnectionPoint.END;

	private FabrikJoint3D mJoint = new FabrikJoint3D();

	private Vector3f mStartLocation = new Vector3f();

	private Vector3f mEndLocation = new Vector3f();

	private String mName;

	private float mLength;

	FabrikBone3D() { }

	public FabrikBone3D(Vector3f startLocation, Vector3f endLocation)
	{
		mStartLocation.set(startLocation);
		mEndLocation.set(endLocation);
		setLength( startLocation.distance(endLocation) );
	}

	public FabrikBone3D(Vector3f startLocation, Vector3f endLocation, String name)
	{
		this(startLocation, endLocation);
		setName(name);
	}

	public FabrikBone3D(Vector3f startLocation, Vector3f directionUV, float length)
	{
		setLength(length);
		if ( directionUV.length() <= 0.0f ) {
		  throw new IllegalArgumentException("Direction cannot be a zero vector");
		}

		setLength(length);
		mStartLocation.set(startLocation);
		mEndLocation.set(mStartLocation).add(new Vector3f(directionUV).normalize().mul(length, new Vector3f()));
	}

	public FabrikBone3D(Vector3f startLocation, Vector3f directionUV, float length, String name)
	{
		this(startLocation, directionUV, length);
		setName(name);
	}

	public FabrikBone3D(FabrikBone3D source)
	{
		mStartLocation.set(source.mStartLocation);
		mEndLocation.set(source.mEndLocation);
		mJoint.set(source.mJoint);

		mName                = source.mName;
		mLength              = source.mLength;
		mBoneConnectionPoint = source.mBoneConnectionPoint;
	}

	public float length() { return mLength; }

	public float liveLength() { return mStartLocation.distance(mEndLocation); }

	public void setBoneConnectionPoint(BoneConnectionPoint bcp) { mBoneConnectionPoint = bcp; }

	public BoneConnectionPoint getBoneConnectionPoint() { return mBoneConnectionPoint; }

	public Vector3f getStartLocation() { return mStartLocation; }

	public Vector3f getEndLocation() { return mEndLocation; }

	public void setJoint(FabrikJoint3D joint) { mJoint.set(joint); }

	public FabrikJoint3D getJoint() { return mJoint; }

	public JointType getJointType() { return mJoint.getJointType(); }

	public void setHingeJointClockwiseConstraintDegs(float angleDegs) { mJoint.setHingeJointClockwiseConstraintDegs(angleDegs); }

	public float getHingeJointClockwiseConstraintDegs() { return mJoint.getHingeClockwiseConstraintDegs(); }

	public void setHingeJointAnticlockwiseConstraintDegs(float angleDegs) { mJoint.setHingeJointAnticlockwiseConstraintDegs(angleDegs); }

	public float getHingeJointAnticlockwiseConstraintDegs() { return mJoint.getHingeAnticlockwiseConstraintDegs(); }

	public void setBallJointConstraintDegs(float angleDegs)
	{
		if (angleDegs < 0.0f || angleDegs > 180.0f)
		{
			throw new IllegalArgumentException("Rotor constraints for ball joints must be in the range 0.0f to 180.0f degrees inclusive.");
		}
		mJoint.setBallJointConstraintDegs(angleDegs);
	}

	public float getBallJointConstraintDegs() { return mJoint.getBallJointConstraintDegs(); }

	public Vector3f getDirectionUV()
	{
		return new Vector3f(mEndLocation).sub(mStartLocation).normalize();
	}

	public float getGlobalPitchDegs()
	{
		return Utils.getGlobalPitchDegs(getDirectionUV());
	}

	public float getGlobalYawDegs()
	{
		return Utils.getGlobalYawDegs(getDirectionUV());
	}

	public void setName(String name) { mName = Utils.getValidatedName(name); }

	public String getName() { return mName; }

	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Start joint location : " + mStartLocation  + NEW_LINE);
		sb.append("End   joint location : " + mEndLocation    + NEW_LINE);
		sb.append("Bone length          : " + mLength         + NEW_LINE);
		return sb.toString();
	}

	public void setStartLocation(Vector3f location)
	{
		mStartLocation.set(location);
	}

	public void setEndLocation(Vector3f location)
	{
		mEndLocation.set(location);
	}

	private void setLength(float length)
	{
		if (length > 0.0f)
		{
			mLength = length;
		}
		else
		{
			throw new IllegalArgumentException("Bone length must be a positive value.");
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((mBoneConnectionPoint == null) ? 0 : mBoneConnectionPoint.hashCode());
		result = prime * result + ((mEndLocation == null) ? 0 : mEndLocation.hashCode());
		result = prime * result + ((mJoint == null) ? 0 : mJoint.hashCode());
		result = prime * result + Float.floatToIntBits(mLength);
		result = prime * result + ((mName == null) ? 0 : mName.hashCode());
		result = prime * result + ((mStartLocation == null) ? 0 : mStartLocation.hashCode());
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
		FabrikBone3D other = (FabrikBone3D) obj;
		if (mBoneConnectionPoint != other.mBoneConnectionPoint) {
			return false;
		}
		if (mEndLocation == null) {
			if (other.mEndLocation != null) {
				return false;
			}
		} else if (!mEndLocation.equals(other.mEndLocation)) {
			return false;
		}
		if (mJoint == null) {
			if (other.mJoint != null) {
				return false;
			}
		} else if (!mJoint.equals(other.mJoint)) {
			return false;
		}
		if (Float.floatToIntBits(mLength) != Float.floatToIntBits(other.mLength)) {
			return false;
		}
		if (mName == null) {
			if (other.mName != null) {
				return false;
			}
		} else if (!mName.equals(other.mName)) {
			return false;
		}
		if (mStartLocation == null) {
			if (other.mStartLocation != null) {
				return false;
			}
		} else if (!mStartLocation.equals(other.mStartLocation)) {
			return false;
		}
		return true;
	}

}
