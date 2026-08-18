package com.minispring.boot;

/**
 * 启动横幅：打印框架名 + 版本 + 启动耗时，是「一条 run() 启动完成」最直观的落地信号。
 */
public final class Banner {

    /** 34：优先读 jar 清单里的 Implementation-Version（构建期由 pom 注入，永不漂移）；
     *  IDE 直跑等无清单场景回退到与 pom 同步维护的字面量。 */
    private static final String VERSION = resolveVersion();

    private Banner() {
    }

    private static String resolveVersion() {
        String version = Banner.class.getPackage().getImplementationVersion();
        return version != null ? version : "0.1.0-SNAPSHOT";
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