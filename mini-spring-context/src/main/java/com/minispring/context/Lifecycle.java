package com.minispring.context;

/**
 * 生命周期组件：容器 refresh 完成后由启动器统一 {@link #start()}，JVM 关闭时逆序 {@link #stop()}。
 *
 * <p>典型用途：内嵌 Web 服务器的启动/停止。它与 {@link DisposableBean} 的分工——
 * DisposableBean 管「Bean 自身资源释放」，Lifecycle 管「随容器进程起停的运行期组件」。
 *
 * <p>接口放在 context（而非 web/boot）：启动器（boot）只依赖本接口即可驱动一切可起停组件，
 * 无需感知任何具体实现——这正是 {@code spring-boot} 只依赖 {@code spring-context} 的
 * {@code Lifecycle} 而不依赖 Tomcat 的原因（D45 收口：optional 依赖下 boot 不得硬引用 web 类）。
 */
public interface Lifecycle {

    /** 容器就绪后启动组件（在「启动完成」事件广播之前）。 */
    void start();

    /** 停止组件（关闭时逆序调用）。 */
    void stop();
}
