package org.gradle.profiler.perfetto.jfr;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.jspecify.annotations.Nullable;

/**
 * Fluent, null-tolerant access to {@link RecordedObject} fields, including nested objects.
 */
public final class JfrRecordFields {
    private final RecordedObject record;
    private final String eventType;

    private JfrRecordFields(RecordedObject record, String eventType) {
        this.record = record;
        this.eventType = eventType;
    }

    public static JfrRecordFields of(RecordedEvent event) {
        return new JfrRecordFields(event, event.getEventType().getName());
    }

    @Nullable
    public String nullableString(String field) {
        return record.hasField(field) ? record.getString(field) : null;
    }

    public String stringOr(String field, String fallback) {
        String value = nullableString(field);
        return value != null ? value : fallback;
    }

    public long longOrZero(String field) {
        return record.hasField(field) ? record.getLong(field) : 0L;
    }

    public double doubleOrZero(String field) {
        return record.hasField(field) ? record.getDouble(field) : 0d;
    }

    public boolean booleanOrFalse(String field) {
        return record.hasField(field) && record.getBoolean(field);
    }

    public long requiredPositiveLong(String field) {
        if (!record.hasField(field)) {
            throw new IllegalArgumentException("Missing required positive field '" + field + "' on " + eventType);
        }
        long value = record.getLong(field);
        if (value <= 0) {
            throw new IllegalArgumentException("Expected positive field '" + field + "' on " + eventType + " but got " + value);
        }
        return value;
    }

    @Nullable
    public JfrRecordFields object(String field) {
        if (!record.hasField(field)) {
            return null;
        }
        return record.getValue(field) instanceof RecordedObject nested
            ? new JfrRecordFields(nested, eventType)
            : null;
    }
}
