package src;

import java.util.*;

/**
 * A lightweight, zero-dependency JSON parser written in pure Java.
 * Parses JSON strings into standard Java data structures:
 * - JSON Object -> Map<String, Object>
 * - JSON Array  -> List<Object>
 * - JSON String -> String
 * - JSON Number -> Double or Long
 * - JSON Bool   -> Boolean
 * - JSON Null   -> null
 */
public class JsonParser {
    private final String src;
    private int pos = 0;

    public JsonParser(String src) {
        this.src = src;
    }

    public static Object parse(String json) {
        return new JsonParser(json).parseValue();
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        skipWhitespace();
        if (pos >= src.length()) {
            return '\0';
        }
        return src.charAt(pos);
    }

    private char next() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input at position " + pos);
        }
        return src.charAt(pos++);
    }

    public Object parseValue() {
        char c = peek();
        if (c == '{') {
            return parseObject();
        } else if (c == '[') {
            return parseArray();
        } else if (c == '"') {
            return parseString();
        } else if (c == 't' || c == 'f') {
            return parseBoolean();
        } else if (c == 'n') {
            return parseNull();
        } else if (c == '-' || Character.isDigit(c)) {
            return parseNumber();
        }
        throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        next(); // consume '{'
        while (true) {
            skipWhitespace();
            if (peek() == '}') {
                next();
                return map;
            }
            String key = parseString();
            skipWhitespace();
            char colon = next();
            if (colon != ':') {
                throw new IllegalArgumentException("Expected ':' after key at position " + pos);
            }
            Object val = parseValue();
            map.put(key, val);
            skipWhitespace();
            char nextChar = peek();
            if (nextChar == ',') {
                next();
            } else if (nextChar == '}') {
                next();
                return map;
            } else {
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
        }
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        next(); // consume '['
        while (true) {
            skipWhitespace();
            if (peek() == ']') {
                next();
                return list;
            }
            Object val = parseValue();
            list.add(val);
            skipWhitespace();
            char nextChar = peek();
            if (nextChar == ',') {
                next();
            } else if (nextChar == ']') {
                next();
                return list;
            } else {
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
        }
    }

    private String parseString() {
        next(); // consume opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            } else if (c == '\\') {
                if (pos >= src.length()) break;
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 <= src.length()) {
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        break;
                    default: sb.append(esc); break;
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string literal");
    }

    private Boolean parseBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        } else if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
    }

    private Object parseNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalArgumentException("Invalid null literal at position " + pos);
    }

    private Number parseNumber() {
        int start = pos;
        if (src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        boolean isFloating = false;
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloating = true;
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloating = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String numStr = src.substring(start, pos);
        if (isFloating) {
            return Double.parseDouble(numStr);
        } else {
            return Long.parseLong(numStr);
        }
    }
}
