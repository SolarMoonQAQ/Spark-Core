package au.edu.federation.caliko;

import au.edu.federation.utils.Utils;
import org.joml.Vector3f;

import java.io.Serializable;

public class FabrikJoint3D implements Serializable
{
	private static final long serialVersionUID = 1L;
	
	private static final String NEW_LINE = System.lineSeparator();
	
	public enum JointType
	{
		BALL,
		GLOBAL_HINGE,
		LOCAL_HINGE
	}
	
	public static final float MIN_CONSTRAINT_ANGLE_DEGS = 0.0f;
	public static final float MAX_CONSTRAINT_ANGLE_DEGS = 180.0f;
	
	private float mRotorConstraintDegs = MAX_CONSTRAINT_ANGLE_DEGS;
	private float mHingeClockwiseConstraintDegs = MAX_CONSTRAINT_ANGLE_DEGS;
	private float mHingeAnticlockwiseConstraintDegs = MAX_CONSTRAINT_ANGLE_DEGS;
	
	private Vector3f mRotationAxisUV = new Vector3f();
	private Vector3f mReferenceAxisUV = new Vector3f();
	
	private JointType mJointType = JointType.BALL;

	public FabrikJoint3D() { }
	
	public FabrikJoint3D(FabrikJoint3D source) { this.set(source); }
	
	public FabrikJoint3D clone(FabrikJoint3D source) { return new FabrikJoint3D(source); }
	
	public void set(FabrikJoint3D source)
	{
		mJointType                        = source.mJointType;
		mRotorConstraintDegs              = source.mRotorConstraintDegs;
		mHingeClockwiseConstraintDegs     = source.mHingeClockwiseConstraintDegs;
		mHingeAnticlockwiseConstraintDegs = source.mHingeAnticlockwiseConstraintDegs;
				
		mRotationAxisUV.set(source.mRotationAxisUV);
		mReferenceAxisUV.set(source.mReferenceAxisUV);
	}
	
	public void setAsBallJoint(float constraintAngleDegs)
	{
		FabrikJoint3D.validateConstraintAngleDegs(constraintAngleDegs);
		mRotorConstraintDegs = constraintAngleDegs;		
		mJointType = JointType.BALL;
	}
	
	public void setHinge(JointType jointType, Vector3f rotationAxis, float clockwiseConstraintDegs, float anticlockwiseConstraintDegs, Vector3f referenceAxis)
	{
		if ( !Utils.approximatelyEquals( Utils.dotProduct(rotationAxis, referenceAxis), 0.0f, 0.01f) )
		{
			float angleDegs = (float)Math.toDegrees(rotationAxis.angle(referenceAxis));
			throw new IllegalArgumentException("The reference axis must be in the plane of the hinge rotation axis - angle between them is currently: " + angleDegs);
		}
		
		FabrikJoint3D.validateConstraintAngleDegs(clockwiseConstraintDegs);
		FabrikJoint3D.validateConstraintAngleDegs(anticlockwiseConstraintDegs);
		FabrikJoint3D.validateAxis(rotationAxis);
		FabrikJoint3D.validateAxis(referenceAxis);
		
		mHingeClockwiseConstraintDegs     = clockwiseConstraintDegs;
		mHingeAnticlockwiseConstraintDegs = anticlockwiseConstraintDegs;
		mJointType                        = jointType;
		mRotationAxisUV.set( new Vector3f(rotationAxis).normalize() );
		mReferenceAxisUV.set( new Vector3f(referenceAxis).normalize() );
	}
	
	public void setAsGlobalHinge(Vector3f globalRotationAxis, float cwConstraintDegs, float acwConstraintDegs, Vector3f globalReferenceAxis)
	{
		setHinge(JointType.GLOBAL_HINGE, globalRotationAxis, cwConstraintDegs, acwConstraintDegs, globalReferenceAxis);
	}
	
	public void setAsLocalHinge(Vector3f localRotationAxis, float cwConstraintDegs, float acwConstraintDegs, Vector3f localReferenceAxis)
	{
		setHinge(JointType.LOCAL_HINGE, localRotationAxis, cwConstraintDegs, acwConstraintDegs, localReferenceAxis);
	}
	
	public float getHingeClockwiseConstraintDegs()
	{
		if ( mJointType != JointType.BALL )
		{
			return mHingeClockwiseConstraintDegs;
		}
		else
		{
			throw new RuntimeException("Joint type is JointType.BALL - it does not have hinge constraint angles.");
		}		
	}
	
	public float getHingeAnticlockwiseConstraintDegs()
	{
		if ( mJointType != JointType.BALL )
		{
			return mHingeAnticlockwiseConstraintDegs;
		}
		else
		{
			throw new RuntimeException("Joint type is JointType.BALL - it does not have hinge constraint angles.");
		}
	}
	
	public void setBallJointConstraintDegs(float angleDegs)
	{
		FabrikJoint3D.validateConstraintAngleDegs(angleDegs);
		
		if (mJointType == JointType.BALL)
		{
			mRotorConstraintDegs = angleDegs;
		}
		else
		{
			throw new RuntimeException("This joint is of type: " + mJointType + " - only joints of type JointType.BALL have a ball joint constraint angle.");
		}
	}
	
	public float getBallJointConstraintDegs()
	{
		if (mJointType == JointType.BALL)
		{
			return mRotorConstraintDegs;
		}
		else
		{
			throw new RuntimeException("This joint is not of type JointType.BALL - it does not have a ball joint constraint angle.");
		}
	}
	
	public void setHingeJointClockwiseConstraintDegs(float angleDegs)
	{
		FabrikJoint3D.validateConstraintAngleDegs(angleDegs);
		
		if ( mJointType != JointType.BALL )
		{
			mHingeClockwiseConstraintDegs = angleDegs;
		}
		else
		{
			throw new RuntimeException("Joint type is JointType.BALL - it does not have hinge constraint angles.");
		}
	}
	
	public void setHingeJointAnticlockwiseConstraintDegs(float angleDegs)
	{
		FabrikJoint3D.validateConstraintAngleDegs(angleDegs);
		
		if ( mJointType != JointType.BALL )
		{
			mHingeAnticlockwiseConstraintDegs = angleDegs;
		}
		else
		{
			throw new RuntimeException("Joint type is JointType.BALL - it does not have hinge constraint angles.");
		}
	}
	
	public void setHingeRotationAxis(Vector3f axis)
	{
		FabrikJoint3D.validateAxis(axis);
		
		if ( mJointType != JointType.BALL )
		{
			mRotationAxisUV.set( new Vector3f(axis).normalize() );
		}
		else
		{
			throw new RuntimeException("Joint type is JointType.BALL - it does not have a hinge rotation axis.");
		}
	}
	
	public Vector3f getHingeReferenceAxis()
	{	
		if ( mJointType != JointType.BALL )
		{
			return mReferenceAxisUV;
		}
		else
		{
			throw new RuntimeException("Joint type is JointType.BALL - it does not have a hinge reference axis.");
		}
	}
	
	public void setHingeReferenceAxis(Vector3f referenceAxis)
	{
		FabrikJoint3D.validateAxis(referenceAxis);
		
		if ( mJointType != JointType.BALL )
		{
			mReferenceAxisUV.set( new Vector3f(referenceAxis).normalize() );
		}
		else
		{
			throw new RuntimeException("Joint type is JointType.BALL - it does not have a hinge reference axis.");
		}
	}
	
	public Vector3f getHingeRotationAxis()
	{	
		if ( mJointType != JointType.BALL )
		{
			return mRotationAxisUV;
		}
		else
		{
			throw new RuntimeException("Joint type is JointType.BALL - it does not have a hinge rotation axis.");
		}
	}
	
	public JointType getJointType() { return mJointType; }
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		
		switch (mJointType)
		{
			case BALL:
				sb.append("Joint type: Ball" + NEW_LINE);
				sb.append("Constraint angle: " + mRotorConstraintDegs + NEW_LINE);
				break;
			case GLOBAL_HINGE:
			case LOCAL_HINGE:
				if (mJointType == JointType.GLOBAL_HINGE)
				{
					sb.append("Joint type                    : Global hinge" + NEW_LINE);
				}
				else
				{
					sb.append("Joint type                    : Local hinge" + NEW_LINE);
				}
				sb.append("Rotation axis                 : " + mRotationAxisUV + NEW_LINE);
				sb.append("Reference axis                : " + mReferenceAxisUV + NEW_LINE);
				sb.append("Anticlockwise constraint angle: " + mHingeClockwiseConstraintDegs + NEW_LINE);
				sb.append("Clockwise constraint angle    : " + mHingeClockwiseConstraintDegs + NEW_LINE);
				break;
		}
		
		return sb.toString();
	}
	
	private static void validateConstraintAngleDegs(float angleDegs)
	{
		if (angleDegs < MIN_CONSTRAINT_ANGLE_DEGS || angleDegs > MAX_CONSTRAINT_ANGLE_DEGS)
		{
			throw new IllegalArgumentException("Constraint angles must be within the range " + MIN_CONSTRAINT_ANGLE_DEGS + " to " + MAX_CONSTRAINT_ANGLE_DEGS + " inclusive.");
		}
	}
	
	private static void validateAxis(Vector3f axis)
	{
		if ( axis.length() <= 0.0f )
		{
			throw new IllegalArgumentException("Provided axis is illegal - it has a magnitude of zero.");
		}
	}

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Float.floatToIntBits(mHingeAnticlockwiseConstraintDegs);
    result = prime * result + Float.floatToIntBits(mHingeClockwiseConstraintDegs);
    result = prime * result + ((mJointType == null) ? 0 : mJointType.hashCode());
    result = prime * result + ((mReferenceAxisUV == null) ? 0 : mReferenceAxisUV.hashCode());
    result = prime * result + ((mRotationAxisUV == null) ? 0 : mRotationAxisUV.hashCode());
    result = prime * result + Float.floatToIntBits(mRotorConstraintDegs);
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
    FabrikJoint3D other = (FabrikJoint3D) obj;
    if (Float.floatToIntBits(mHingeAnticlockwiseConstraintDegs) != Float
        .floatToIntBits(other.mHingeAnticlockwiseConstraintDegs)) {
      return false;
    }
    if (Float.floatToIntBits(mHingeClockwiseConstraintDegs) != Float
        .floatToIntBits(other.mHingeClockwiseConstraintDegs)) {
      return false;
    }
    if (mJointType != other.mJointType) {
      return false;
    }
    if (mReferenceAxisUV == null) {
      if (other.mReferenceAxisUV != null) {
        return false;
      }
    } else if (!mReferenceAxisUV.equals(other.mReferenceAxisUV)) {
      return false;
    }
    if (mRotationAxisUV == null) {
      if (other.mRotationAxisUV != null) {
        return false;
      }
    } else if (!mRotationAxisUV.equals(other.mRotationAxisUV)) {
      return false;
    }
    if (Float.floatToIntBits(mRotorConstraintDegs) != Float.floatToIntBits(other.mRotorConstraintDegs)) {
      return false;
    }
    return true;
  }

}
