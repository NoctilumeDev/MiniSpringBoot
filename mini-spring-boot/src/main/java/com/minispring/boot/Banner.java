package com.minispring.boot;

/**
 * 启动横幅：打印框架名 + 版本 + 启动耗时，是「一条 run() 启动完成」最直观的落地信号。
 */
public final class Banner {

    private static final String VERSION = "0.7.0";

    private Banner() {
    }

    public static void print(long startupMillis) {
        System.out.println();
        System.out.println("  ============================================");
        System.out.println("   MiniSpringBoot   (v" + VERSION + ")");
        System.out.println("   Startup in " + startupMillis + " ms");
        System.out.println("  ============================================");
        System.out.println();
    }
}