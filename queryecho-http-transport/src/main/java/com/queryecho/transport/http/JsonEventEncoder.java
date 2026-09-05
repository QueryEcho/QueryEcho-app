package com.queryecho.transport.http;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** SDK record DTO와 JSON 값만 지원한다. Jackson 버전과 무관한 wire encoder. */
public final class JsonEventEncoder implements EventEncoder {
    @Override
    public String encode(Object value) throws ReflectiveOperationException {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private void append(StringBuilder out, Object value) throws ReflectiveOperationException {
        if (value == null) { out.append("null"); }
        else if (value instanceof String || value instanceof UUID || value instanceof Instant || value instanceof Enum<?>) {
            quote(out, value instanceof Enum<?> e ? e.name() : value.toString());
        } else if (value instanceof Boolean) { out.append(value); }
        else if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d) || number instanceof Float f && !Float.isFinite(f)) {
                throw new IllegalArgumentException("JSON cannot encode non-finite numbers");
            }
            out.append(number);
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) out.append(',');
                first = false;
                append(out, item);
            }
            out.append(']');
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("JSON keys must be strings");
                if (!first) out.append(',');
                first = false;
                quote(out, key); out.append(':'); append(out, entry.getValue());
            }
            out.append('}');
        } else if (value.getClass().isRecord() && value.getClass().getPackageName().equals("com.queryecho.core.dto")) {
            out.append('{');
            boolean first = true;
            for (RecordComponent field : value.getClass().getRecordComponents()) {
                if (!first) out.append(',');
                first = false;
                quote(out, field.getName()); out.append(':'); append(out, field.getAccessor().invoke(value));
            }
            out.append('}');
        } else { throw new IllegalArgumentException("Unsupported wire value: " + value.getClass().getName()); }
    }

    private void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || Character.isSurrogate(c)) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
