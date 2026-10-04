package com.jme3.bullet.util;

public class NativeLibrary {
    public static native boolean isDoublePrecision();

    public static native boolean isThreadSafe();

    public static native boolean isDebug();

    public static native boolean isQuickprof();

    public static native int countThreads();

    public static native String versionNumber();

    /** JNI_OnLoad / postInit callback target. */
    private static void postInitialization() {
        System.out.println("  [postInitialization callback fired]");
    }

    private static void reinitialization() {
    }

    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        System.out.println("  versionNumber     = " + versionNumber());
        System.out.println("  isDoublePrecision = " + isDoublePrecision());
        System.out.println("  isThreadSafe      = " + isThreadSafe());
        System.out.println("  isDebug           = " + isDebug());
        System.out.println("  isQuickprof       = " + isQuickprof());
        System.out.println("  countThreads      = " + countThreads());
    }
}
