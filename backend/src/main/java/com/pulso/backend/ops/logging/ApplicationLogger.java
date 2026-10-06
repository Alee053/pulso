package com.pulso.backend.ops.logging;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Writes consistent, structured diagnostic events for backend modules. */
public final class ApplicationLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationLogger.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REDACTED = "[REDACTED]";
    private static final int MAX_DEPTH = 6;
    private static final int MAX_ITEMS = 100;
    private static final int MAX_TEXT_LENGTH = 2_048;
    private static final int MAX_EXCEPTION_DEPTH = 8;
    private static final int MAX_STACK_FRAMES = 64;
    private static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "password",
            "passwd",
            "passphrase",
            "secret",
            "token",
            "authorization",
            "credential",
            "apikey",
            "accesskey",
            "privatekey",
            "signingkey",
            "encryptionkey",
            "servicerolekey",
            "cookie",
            "sessionid",
            "email",
            "phone",
            "address",
            "ssn",
            "socialsecurity",
            "birthdate",
            "dateofbirth",
            "cardnumber",
            "cvv");
    private static final String FLEXIBLE_KEY_SEPARATOR = "[\\s._-]*";
    private static final String SENSITIVE_ASSIGNMENT_NAMES = String.join(
            "|",
            "supabase" + FLEXIBLE_KEY_SEPARATOR + "service" + FLEXIBLE_KEY_SEPARATOR + "role"
                    + FLEXIBLE_KEY_SEPARATOR + "key",
            "service" + FLEXIBLE_KEY_SEPARATOR + "role" + FLEXIBLE_KEY_SEPARATOR + "key",
            "pass(?:" + FLEXIBLE_KEY_SEPARATOR + "(?:word|phrase)|wd)",
            "access" + FLEXIBLE_KEY_SEPARATOR + "(?:token|key)",
            "refresh" + FLEXIBLE_KEY_SEPARATOR + "token",
            "private" + FLEXIBLE_KEY_SEPARATOR + "key",
            "signing" + FLEXIBLE_KEY_SEPARATOR + "key",
            "encryption" + FLEXIBLE_KEY_SEPARATOR + "key",
            "token",
            "secret" + FLEXIBLE_KEY_SEPARATOR + "key",
            "secret",
            "client" + FLEXIBLE_KEY_SEPARATOR + "secret",
            "client" + FLEXIBLE_KEY_SEPARATOR + "credentials?",
            "api" + FLEXIBLE_KEY_SEPARATOR + "key",
            "authorization",
            "credentials?",
            "cookie",
            "session" + FLEXIBLE_KEY_SEPARATOR + "id",
            "email",
            "phone(?:" + FLEXIBLE_KEY_SEPARATOR + "number)?",
            "address",
            "ssn",
            "social" + FLEXIBLE_KEY_SEPARATOR + "security",
            "birth" + FLEXIBLE_KEY_SEPARATOR + "date",
            "date" + FLEXIBLE_KEY_SEPARATOR + "of" + FLEXIBLE_KEY_SEPARATOR + "birth",
            "card" + FLEXIBLE_KEY_SEPARATOR + "number",
            "cvv");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?is)(\\b(?:" + SENSITIVE_ASSIGNMENT_NAMES + ")\\b[\\\"']?\\s*[:=]\\s*).*");
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern BASIC_TOKEN = Pattern.compile(
            "(?i)\\bBasic\\s+[A-Za-z0-9+/]+=*");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "(?<![A-Za-z0-9])(?:\\+\\d{1,3}[ .() -]?)?(?:\\(?\\d{2,4}\\)?[ .() -]?)?\\d{3,4}[ .() -]?\\d{4}(?![A-Za-z0-9])");
    private static final Pattern STREET_ADDRESS = Pattern.compile(
            "(?i)\\b(?:calle|avenida|av\\.?|pasaje|plaza|carretera|street|st\\.?|road|rd\\.?|boulevard|blvd\\.?)\\s+[\\p{L}\\p{N}][\\p{L}\\p{N} .#/-]{0,60}\\b\\d+[A-Za-z]?\\b");
    private static final Pattern URI_CREDENTIALS = Pattern.compile(
            "(?i)([a-z][a-z0-9+.-]*://)[^:/\\s@]+:[^@\\s/]+@");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\b");
    private static final Pattern KNOWN_TOKEN = Pattern.compile(
            "(?i)\\b(?:sk_(?:live|test)_|sk-(?:proj|svcacct|ant|live|test)-|gh[pousr]_|xox[baprs]-|sb_secret_)[A-Za-z0-9_-]{8,}\\b");

    private ApplicationLogger() {
    }

    public static void info(LogContext context, String message, Map<String, ?> metadata) {
        write(Level.INFO, context, message, metadata, null);
    }

    public static void warn(LogContext context, String message, Map<String, ?> metadata) {
        write(Level.WARN, context, message, metadata, null);
    }

    public static void error(
            LogContext context,
            String message,
            Map<String, ?> metadata,
            Throwable exception) {
        write(Level.ERROR, context, message, metadata, exception);
    }

    private static void write(
            Level level,
            LogContext context,
            String message,
            Map<String, ?> metadata,
            Throwable exception) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("level", level.name());
        event.put("module", sanitizeIdentifierText(context.module()));
        event.put("operation", sanitizeIdentifierText(context.operation()));
        putIfPresent(event, "requestId", context.requestId());
        putIfPresent(event, "userId", context.userId());
        event.put("message", sanitizeText(message));
        event.put("metadata", safeMap(metadata == null ? Map.of() : metadata));
        if (exception != null) {
            event.put("exception", safeException(exception));
        }

        try {
            emit(level, JSON.writeValueAsString(event));
        } catch (JacksonException serializationFailure) {
            LOGGER.error("Unable to serialize an application log event.");
        }
    }

    private static void emit(Level level, String event) {
        switch (level) {
            case INFO -> LOGGER.info(event);
            case WARN -> LOGGER.warn(event);
            case ERROR -> LOGGER.error(event);
        }
    }

    private static void putIfPresent(Map<String, Object> event, String name, String value) {
        if (value != null && !value.isBlank()) {
            event.put(name, sanitizeIdentifierText(value));
        }
    }

    private static Map<String, Object> safeMap(Map<?, ?> values) {
        Object safe = safeValue(values, null, 0, new IdentityHashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) safe;
        return result;
    }

    private static Object safeValue(
            Object value,
            String key,
            int depth,
            IdentityHashMap<Object, Boolean> visited) {
        if (key != null && isSensitiveKey(key)) {
            return REDACTED;
        }
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            return isIdentifierKey(key) ? sanitizeIdentifierText(text) : sanitizeText(text);
        }
        if (value instanceof Character character) {
            return sanitizeText(character.toString());
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return sanitizeText(enumValue.name());
        }
        if (value instanceof Double number) {
            return Double.isFinite(number) ? number : number.toString();
        }
        if (value instanceof Float number) {
            return Float.isFinite(number) ? number : number.toString();
        }
        if (isJsonNumber(value)) {
            return value;
        }
        if (depth >= MAX_DEPTH) {
            return "[MAX_DEPTH]";
        }
        if (value instanceof Map<?, ?> map) {
            if (visited.put(value, Boolean.TRUE) != null) {
                return "[CIRCULAR]";
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_ITEMS) {
                    safe.put("_truncated", true);
                    break;
                }
                if (entry.getKey() instanceof String entryKey) {
                    String safeKey = isSensitiveKey(entryKey)
                            ? REDACTED
                            : truncate(sanitizeText(entryKey), 128);
                    Object safeEntryValue = REDACTED.equals(safeKey)
                            ? REDACTED
                            : safeValue(entry.getValue(), entryKey, depth + 1, visited);
                    safe.put(safeKey, safeEntryValue);
                } else {
                    safe.put("[unsupported-key]", "[REDACTED]");
                }
            }
            visited.remove(value);
            return safe;
        }
        if (value instanceof Iterable<?> iterable) {
            if (visited.put(value, Boolean.TRUE) != null) {
                return "[CIRCULAR]";
            }
            List<Object> safe = new ArrayList<>();
            int count = 0;
            for (Object item : iterable) {
                if (count++ >= MAX_ITEMS) {
                    safe.add("[TRUNCATED]");
                    break;
                }
                safe.add(safeValue(item, null, depth + 1, visited));
            }
            visited.remove(value);
            return safe;
        }
        if (value.getClass().isArray()) {
            if (value instanceof char[]) {
                return REDACTED;
            }
            if (value instanceof byte[]) {
                return "[binary data omitted]";
            }
            if (visited.put(value, Boolean.TRUE) != null) {
                return "[CIRCULAR]";
            }
            int length = Math.min(Array.getLength(value), MAX_ITEMS);
            List<Object> safe = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                safe.add(safeValue(Array.get(value, index), null, depth + 1, visited));
            }
            if (Array.getLength(value) > MAX_ITEMS) {
                safe.add("[TRUNCATED]");
            }
            visited.remove(value);
            return safe;
        }
        return "[unsupported:" + sanitizeText(value.getClass().getSimpleName()) + "]";
    }

    private static boolean isJsonNumber(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    private static boolean isIdentifierKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.equals("id")
                || normalized.endsWith("uuid")
                || normalized.endsWith("identifier")
                || key.matches(".*(?:Id|ID|[_-]id)$");
    }

    private static String sanitizeText(String value) {
        return sanitizeText(value, true);
    }

    private static String sanitizeIdentifierText(String value) {
        return sanitizeText(value, false);
    }

    private static String sanitizeText(String value, boolean redactContactInfo) {
        String sanitized = JWT.matcher(value).replaceAll(REDACTED);
        sanitized = BEARER_TOKEN.matcher(sanitized).replaceAll("Bearer " + REDACTED);
        sanitized = BASIC_TOKEN.matcher(sanitized).replaceAll("Basic " + REDACTED);
        sanitized = URI_CREDENTIALS.matcher(sanitized).replaceAll("$1" + REDACTED + "@");
        sanitized = EMAIL.matcher(sanitized).replaceAll(REDACTED);
        if (redactContactInfo) {
            sanitized = STREET_ADDRESS.matcher(sanitized).replaceAll(REDACTED);
            sanitized = PHONE_NUMBER.matcher(sanitized).replaceAll(REDACTED);
        }
        sanitized = SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1" + REDACTED);
        sanitized = KNOWN_TOKEN.matcher(sanitized).replaceAll(REDACTED);
        return truncate(sanitized, MAX_TEXT_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "[TRUNCATED]";
    }

    private static Map<String, Object> safeException(Throwable exception) {
        return safeException(exception, new IdentityHashMap<>(), 0);
    }

    private static Map<String, Object> safeException(
            Throwable exception,
            IdentityHashMap<Throwable, Boolean> visited,
            int depth) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", exception.getClass().getSimpleName());
        if (visited.put(exception, Boolean.TRUE) != null) {
            details.put("message", "[CIRCULAR]");
            return details;
        }
        details.put("message", sanitizeText(exception.getMessage() == null ? "" : exception.getMessage()));
        details.put("stackTrace", Arrays.stream(exception.getStackTrace())
                .limit(MAX_STACK_FRAMES)
                .map(StackTraceElement::toString)
                .map(ApplicationLogger::sanitizeText)
                .toList());
        Throwable cause = exception.getCause();
        if (cause != null) {
            if (depth >= MAX_EXCEPTION_DEPTH) {
                details.put("cause", Map.of("type", cause.getClass().getSimpleName(), "message", "[TRUNCATED]"));
            } else {
                details.put("cause", safeException(cause, visited, depth + 1));
            }
        }
        visited.remove(exception);
        return details;
    }

    private enum Level {
        INFO,
        WARN,
        ERROR
    }
}
