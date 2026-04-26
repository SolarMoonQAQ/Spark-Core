package au.edu.federation.utils;

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

	public static void validateDirectionUV(Vec3f directionUV)
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

}
