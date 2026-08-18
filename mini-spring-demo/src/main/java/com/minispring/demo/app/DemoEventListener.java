package com.minispring.demo.app;

import com.minispring.context.ApplicationEvent;
import com.minispring.context.ApplicationListener;
import com.minispring.context.annotation.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 事件监听器：接收所有生命周期事件并「按序留存」。
 * 用来证明容器在启动链路中确实按「刷新完成 → 启动完成」顺序广播（关闭时还有 Closed）。
 */
@Component
public class DemoEventListener implements ApplicationListener<ApplicationEvent> {

    private final List<String> recordedEvents = new ArrayList<>();

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        String name = event.getClass().getSimpleName();
        recordedEvents.add(name);
        System.out.println("    [event] 收到 " + name + " @t=" + event.getTimestamp());
    }

    public List<String> getRecordedEvents() {
        return recordedEvents;
    }
}