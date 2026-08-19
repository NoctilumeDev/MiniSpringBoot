package com.minispring.config.support;

import com.minispring.core.env.StandardEnvironment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * L3（M0-M9 复审第二轮）的约束用例：profile 层优先级对齐 Spring——
 * ① 多 profile last-wins：修复前正序遍历 profiles，先激活的插得更靠前而赢（与 Spring 相反），
 *    倒序遍历纠正后后激活的覆盖先激活的；
 * ② 同层 properties &gt; yml：新旧实现均正确（旧代码注释「后插入的优先级更高」因果描述
 *    相反，曾误导复审分析——本用例留作行为锚定防回归）。
 * 测试资源位于 src/test/resources（仅测试期可见，不进发布 jar，不违反 D33）。
 */
class ConfigFilePropertySourceLoaderTest {

    private StandardEnvironment load(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        new ConfigFilePropertySourceLoader().load(environment);
        return environment;
    }

    @Test
    void profileLayerOverridesDefault() {
        assertEquals("from-default", load().getProperty("cfg.k"));
        assertEquals("from-t1", load("t1").getProperty("cfg.k"),
                "profile 层应覆盖默认层");
    }

    @Test
    void lastActivatedProfileWins() {
        // 修复前：正序遍历 profiles，t1 插得更靠前 → t1 赢（与 Spring last-wins 相反）
        assertEquals("from-t2", load("t1", "t2").getProperty("cfg.k"),
                "后激活的 profile 必须覆盖先激活的（Spring last-wins）");
    }

    @Test
    void propertiesBeatsYmlInSameProfileLayer() {
        // 行为锚定（防回归）：同层 properties 必须优先于 yml
        assertEquals("from-properties", load("x").getProperty("cfg.same"),
                "同层 properties 必须优先于 yml（与默认层一致）");
    }

    /** M3：properties 必须按 UTF-8 读取（修复前 Properties.load(InputStream) 固定 ISO-8859-1，中文必乱码）。 */
    @Test
    void propertiesFileReadsUtf8ChineseValues() {
        byte[] bytes = "cfg.name=中文值".getBytes(StandardCharsets.UTF_8);
        InputStream in = new ByteArrayInputStream(bytes);
        Map<String, Object> map = new PropertiesPropertySourceLoader().load(in);
        assertEquals("中文值", map.get("cfg.name"),
                "properties 中文值必须原样读出（UTF-8），不得乱码");
    }
}
