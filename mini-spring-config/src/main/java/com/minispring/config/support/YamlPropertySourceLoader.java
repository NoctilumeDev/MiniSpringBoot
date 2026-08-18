package com.minispring.config.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 YAML 的「教学子集」：缩进映射、标量、列表、注释。
 *
 * <p>核心动作是把「树」拍平成「. 连接的 key」，列表拍平成 {@code key[0]/key[1]…}。
 * 明确不支持 anchor、多行字符串、tag 等冷门特性；值里的 {@code #} 会被当作注释截断。
 */
public class YamlPropertySourceLoader {

    public Map<String, Object> load(InputStream in) {
        List<Line> lines = readLines(in);
        Map<String, Object> flat = new LinkedHashMap<>();
        List<Frame> stack = new ArrayList<>();

        for (Line line : lines) {
            String content = line.content;

            if (content.startsWith("- ") || content.equals("-")) {
                handleListItem(flat, stack, content);
                continue;
            }

            int colon = content.indexOf(':');
            if (colon < 0) {
                throw new IllegalStateException("无法解析的 YAML 行: " + content);
            }
            String key = content.substring(0, colon).trim();
            String value = content.substring(colon + 1).trim();

            // 弹出所有「缩进不深于当前行」的层，让栈顶始终是最近的父级
            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent >= line.indent) {
                stack.remove(stack.size() - 1);
            }

            Frame parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
            String prefix = parent == null ? "" : parent.path;
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (value.isEmpty()) {
                // 空值 = 映射节点，压栈等待它的子项
                stack.add(new Frame(line.indent, fullKey));
            } else {
                flat.put(fullKey, stripQuotes(value));
            }
        }
        return flat;
    }

    /** 剥离标量首尾成对的引号（`"8080"` → `8080`），否则引号会被带进值、拖垮类型转换。 */
    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private void handleListItem(Map<String, Object> flat, List<Frame> stack, String content) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("列表项缺少父级 key: " + content);
        }
        Frame parent = stack.get(stack.size() - 1);
        String item = content.equals("-") ? "" : content.substring(2).trim();
        flat.put(parent.path + "[" + parent.listIndex++ + "]", stripQuotes(item));
    }

    private List<Line> readLines(InputStream in) {
        List<Line> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String withoutComment = stripComment(raw);
                if (withoutComment.trim().isEmpty()) {
                    continue;
                }
                lines.add(new Line(countIndent(raw), withoutComment.trim()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 yaml 失败", e);
        }
        return lines;
    }

    private String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private int countIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static final class Line {
        final int indent;
        final String content;

        Line(int indent, String content) {
            this.indent = indent;
            this.content = content;
        }
    }

    private static final class Frame {
        final int indent;
        final String path;   // 该节点的完整点路径
        int listIndex = 0;   // 该节点下列表项的游标

        Frame(int indent, String path) {
            this.indent = indent;
            this.path = path;
        }
    }
}