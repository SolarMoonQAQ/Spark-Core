package au.edu.federation.utils;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.Random;

public final class Utils
{
	private static final float DEGS_TO_RADS = (float)Math.PI / 180.0f;
	private static final float RADS_TO_DEGS = 180.0f / (float)Math.PI;

	public static final DecimalFormat df = new DecimalFormat("0.000");

	public static final String NEW_LINE = System.lineSeparator();

	public static Random random = new Random();

	public static final int MAX_NAME_LENGTH = 100;

	public static final Vector3f X_AXIS = new Vector3f(1.0f, 0.0f, 0.0f);
	public static final Vector3f Y_AXIS = new Vector3f(0.0f, 1.0f, 0.0f);
	public static final Vector3f Z_AXIS = new Vector3f(0.0f, 0.0f, 1.0f);

	private Utils() {}

	public static void setRandomSeed(int seedValue)
	{
		random = new Random(seedValue);
	}

	public static float randRange(float min, float max) { return random.nextFloat() * (max - min) + min; }

	public static int randRange(int min, int max) { return random.nextInt(max - min) + min; }

	public static float cot(float angleRads) { return (float)( 1.0f / Math.tan(angleRads) ); }

	public static float radiansToDegrees(float angleRads) { return angleRads * RADS_TO_DEGS; }

	public static float degreesToRadians(float angleDegs) { return angleDegs * DEGS_TO_RADS; }

	public static float sign(float value)
	{
		if (value >= 0.0f) {
		  return 1.0f;
		}
		return -1.0f;
	}

	public static void setSeed(int seed) { random = new Random(seed); }

	public static void validateDirectionUV(Vector3f directionUV)
	{
		if ( directionUV.length() <= 0.0f )
		{
			throw new IllegalArgumentException("Vec3f direction unit vector cannot be zero.");
		}
	}

	public static void validateLength(float length)
	{
		if (length < 0.0f)
		{
			throw new IllegalArgumentException("Length must be a greater than or equal to zero.");
		}
	}

	public static float convertRange(float origValue, float origMin, float origMax, float newMin, float newMax)
	{
		float origRange = origMax - origMin;
		float newRange  = newMax  - newMin;

		float newValue;
		if (origRange > -0.000001f && origRange < 0.000001f)
		{
		    newValue = (newMin + newMax) / 2.0f;
		}
		else
		{
			newValue = (((origValue - origMin) * newRange) / origRange) + newMin;
		}

		return newValue;
	}

	public static boolean approximatelyEquals(float a, float b, float tolerance)
	{
		return (Math.abs(a - b) <= tolerance) ? true : false;
	}

	public static String getValidatedName(String name)
	{
		if (name.length() >= Utils.MAX_NAME_LENGTH)
		{
			return name.substring(0, Utils.MAX_NAME_LENGTH);
		}
		else
		{
			return name;
		}
	}

	// ========== Vector3f helper methods (ported from custom Vec3f/Mat3f) ==========

	public static float dotProduct(Vector3f a, Vector3f b)
	{
		Vector3f aNorm = new Vector3f(a).normalize();
		Vector3f bNorm = new Vector3f(b).normalize();
		return aNorm.dot(bNorm);
	}

	public static Vector3f projectOntoPlane(Vector3f v, Vector3f planeNormal, Vector3f dest)
	{
		if (planeNormal.length() <= 0.0f) {
			throw new IllegalArgumentException("Plane normal cannot be a zero vector.");
		}

		Vector3f b = new Vector3f(v).normalize();
		Vector3f n = new Vector3f(planeNormal).normalize();
		float d = b.dot(n);

		return b.sub(n.mul(d, new Vector3f()), dest).normalize();
	}

	public static Vector3f genPerpendicularVectorQuick(Vector3f u, Vector3f dest)
	{
		Vector3f perp;
		if (Math.abs(u.y) < 0.99f)
		{
			perp = new Vector3f(-u.z, 0.0f, u.x);
		}
		else
		{
			perp = new Vector3f(0.0f, u.z, -u.y);
		}
		return perp.normalize(dest);
	}

	public static Vector3f genPerpendicularVectorHM(Vector3f u, Vector3f dest)
	{
		Vector3f a = new Vector3f(Math.abs(u.x), Math.abs(u.y), Math.abs(u.z));

		if (a.x <= a.y && a.x <= a.z)
		{
			return new Vector3f(0.0f, -u.z, u.y).normalize(dest);
		}
		else if (a.y <= a.x && a.y <= a.z)
		{
			return new Vector3f(-u.z, 0.0f, u.x).normalize(dest);
		}
		else
		{
			return new Vector3f(-u.y, u.x, 0.0f).normalize(dest);
		}
	}

	public static float getSignedAngleBetweenDegs(Vector3f referenceVector, Vector3f otherVector, Vector3f normalVector)
	{
		float unsignedAngle = (float)Math.toDegrees(referenceVector.angle(otherVector));
		Vector3f cross = new Vector3f();
		referenceVector.cross(otherVector, cross);
		float sign = Utils.sign(cross.dot(normalVector));
		return unsignedAngle * sign;
	}

	public static Vector3f angleLimitedUnitVectorDegs(Vector3f vecToLimit, Vector3f vecBaseline, float angleLimitDegs, Vector3f dest)
	{
		float angleBetweenVectorsDegs = (float)Math.toDegrees(vecBaseline.angle(vecToLimit));

		if (angleBetweenVectorsDegs > angleLimitDegs)
		{
			Vector3f nBase = new Vector3f(vecBaseline).normalize();
			Vector3f nLimit = new Vector3f(vecToLimit).normalize();
			Vector3f correctionAxis = new Vector3f();
			nBase.cross(nLimit, correctionAxis).normalize();

			return new Vector3f(vecBaseline).rotateAxis((float)Math.toRadians(angleLimitDegs), correctionAxis.x, correctionAxis.y, correctionAxis.z).normalize(dest);
		}
		else
		{
			return new Vector3f(vecToLimit).normalize(dest);
		}
	}

	public static float getGlobalPitchDegs(Vector3f v)
	{
		Vector3f xProjected = Utils.projectOntoPlane(v, X_AXIS, new Vector3f());
		float pitch = (float)Math.toDegrees(new Vector3f(Z_AXIS).negate().angle(xProjected));
		return xProjected.y < 0.0f ? -pitch : pitch;
	}

	public static float getGlobalYawDegs(Vector3f v)
	{
		Vector3f yProjected = Utils.projectOntoPlane(v, Y_AXIS, new Vector3f());
		float yaw = (float)Math.toDegrees(new Vector3f(Z_AXIS).negate().angle(yProjected));
		return yProjected.x < 0.0f ? -yaw : yaw;
	}

	public static Matrix3f createRotationMatrix(Vector3f referenceDirection, Matrix3f dest)
	{
		Matrix3f rotMat = new Matrix3f();

		if (Math.abs(referenceDirection.y) > 0.9999f)
		{
			setZBasis(rotMat, referenceDirection);
			setXBasis(rotMat, new Vector3f(1.0f, 0.0f, 0.0f));
			Vector3f xBasis = getXBasis(rotMat, new Vector3f());
			Vector3f zBasis = getZBasis(rotMat, new Vector3f());
			Vector3f yBasis = new Vector3f();
			xBasis.cross(zBasis, yBasis).normalize();
			setYBasis(rotMat, yBasis);
		}
		else
		{
			setZBasis(rotMat, referenceDirection);
			Vector3f xBasis = new Vector3f();
			referenceDirection.cross(new Vector3f(0.0f, 1.0f, 0.0f), xBasis).normalize();
			setXBasis(rotMat, xBasis);
			Vector3f zBasis = getZBasis(rotMat, new Vector3f());
			Vector3f yBasis = new Vector3f();
			xBasis.cross(zBasis, yBasis).normalize();
			setYBasis(rotMat, yBasis);
		}

		if (dest != null) {
			dest.set(rotMat);
			return dest;
		}
		return rotMat;
	}

	public static void setXBasis(Matrix3f m, Vector3f v) {
		m.setColumn(0, v);
	}

	public static Vector3f getXBasis(Matrix3f m, Vector3f dest) {
		return m.getColumn(0, dest);
	}

	public static void setYBasis(Matrix3f m, Vector3f v) {
		m.setColumn(1, v);
	}

	public static Vector3f getYBasis(Matrix3f m, Vector3f dest) {
		return m.getColumn(1, dest);
	}

	public static void setZBasis(Matrix3f m, Vector3f v) {
		m.setColumn(2, v);
	}

	public static Vector3f getZBasis(Matrix3f m, Vector3f dest) {
		return m.getColumn(2, dest);
	}
}
