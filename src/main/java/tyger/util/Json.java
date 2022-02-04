package tyger.util;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class to generate JSON-string representation of objects.
 */
public class Json {

    public static final String str(final String value) {
        return '\"' + value + '\"';
    }

    public static final String field(final String name, final Object value) {
        return field(name, value.toString());
    }

    public static final String field(final String name, final String value) {
        return str(name) + ':' + value;
    }

    public static final String field_str(final String name, final Object value) {
        return field_str(name, value.toString());
    }

    public static final String field_str(final String name, final String value) {
        return str(name) + ':' + str(value);
    }

    public static final String field_array(final String name, final Collection<?> items) {
        return field_array(name, items.stream().map(Object::toString));
    }

    public static final String field_array(final String name, final String... items) {
        return field_array(name, Stream.of(items));
    }

    public static final String field_array(final String name, final Stream<String> items) {
        return field(name, array(items));
    }

    public static final String object(final String... fields) {
        return '{' + join(fields) + '}';
    }

    public static final String array(Collection<?> items) {
        return array(items.stream().map(Object::toString));
    }

    public static final String array(Stream<String> items) {
        return '[' + join(items) + ']';
    }

    private static final String join(String... parts) {
        return join(Stream.of(parts));
    }

    private static final String join(Stream<String> parts) {
        return parts.collect(Collectors.joining(","));
    }
}
