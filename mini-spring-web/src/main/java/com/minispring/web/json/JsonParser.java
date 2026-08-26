package com.minispring.web.json;

/**
 * 极简 JSON 解析器：递归下降，把 JSON 文本解析成 {@link JsonNode} 树。
 *
 * <p>支持 object / array / string（含常用转义、Unicode 转义）/ number / true / false / null。
 * 严格拒绝重复键、数字前导零、字符串中的裸控制字符和非 JSON 空白；不保证超大整数的映射精度。
 */
public final class JsonParser {

    /** 最大嵌套深度：超过即报错，防恶意深嵌套 JSON 把递归下降打到 StackOverflowError（P0-1）。 */
    static final int MAX_DEPTH = 512;

    private final String text;
    private int pos = 0;

    public JsonParser(String text) {
        this.text = text == null ? "" : text;
    }

    public JsonNode parse() {
        JsonNode node = parseValue(0);
        skipWhitespace();
        if (pos < text.length()) {
            throw error("JSON 结尾有多余字符");
        }
        return node;
    }

    private JsonNode parseValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("JSON 嵌套深度超过上限 " + MAX_DEPTH);
        }
        skipWhitespace();
        if (pos >= text.length()) {
            throw error("预期一个 JSON 值");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return parseObject(depth);
            case '[':
                return parseArray(depth);
            case '"':
                return JsonNode.ofString(parseString());
            case 't':
                expect("true");
                return JsonNode.ofBoolean(true);
            case 'f':
                expect("false");
                return JsonNode.ofBoolean(false);
            case 'n':
                expect("null");
                return JsonNode.ofNull();
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw error("无法识别的字符 '" + c + "'");
        }
    }

    private JsonNode parseObject(int depth) {
        expect('{');
        JsonNode object = JsonNode.object();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            if (object.get(key) != null) {
                throw error("对象包含重复键 '" + key + "'");
            }
            skipWhitespace();
            expect(':');
            object.put(key, parseValue(depth + 1));
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                return object;
            } else {
                throw error("对象内预期 ',' 或 '}'");
            }
        }
    }

    private JsonNode parseArray(int depth) {
        expect('[');
        JsonNode array = JsonNode.array();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            array.add(parseValue(depth + 1));
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                return array;
            } else {
                throw error("数组内预期 ',' 或 ']'");
            }
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("字符串未闭合");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= text.length()) {
                    throw error("转义不完整");
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        sb.append(parseUnicode());
                        break;
                    default:
                        throw error("未知转义 '\\" + e + "'");
                }
            } else {
                if (c < 0x20) {
                    throw error("字符串包含未转义控制字符 0x"
                            + String.format("%02x", (int) c));
                }
                sb.append(c);
            }
        }
    }

    private char parseUnicode() {
        if (pos + 4 > text.length()) {
            throw error("\\u 转义不完整");
        }
        String hex = text.substring(pos, pos + 4);
        pos += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw error("非法 \\u 编码");
        }
    }

    private JsonNode parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (pos >= text.length()) {
            throw error("非法数字");
        }
        if (text.charAt(pos) == '0') {
            pos++;
            if (pos < text.length() && isDigit(text.charAt(pos))) {
                throw error("数字整数部分不允许前导零");
            }
        } else if (text.charAt(pos) >= '1' && text.charAt(pos) <= '9') {
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
            }
        } else {
            throw error("非法数字");
        }
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            int fracStart = pos;
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
            }
            // P4：小数点后必须至少一位数字，`1.` 非法
            if (pos == fracStart) {
                throw error("小数点后缺少数字");
            }
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            int expStart = pos;
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
            }
            // P4：指数后必须至少一位数字，`1e` / `1e+` 非法
            if (pos == expStart) {
                throw error("指数后缺少数字");
            }
        }
        return JsonNode.ofNumber(text.substring(start, pos));
    }

    // ----- 基础助手 -----

    private void expect(char c) {
        if (pos >= text.length() || text.charAt(pos) != c) {
            throw error("预期字符 '" + c + "'");
        }
        pos++;
    }

    private void expect(String literal) {
        if (!text.startsWith(literal, pos)) {
            throw error("预期 '" + literal + "'");
        }
        pos += literal.length();
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("意外到达文本结尾");
        }
        return text.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                return;
            }
            pos++;
        }
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("JSON 解析失败（位置 " + pos + "）: " + message);
    }
}
