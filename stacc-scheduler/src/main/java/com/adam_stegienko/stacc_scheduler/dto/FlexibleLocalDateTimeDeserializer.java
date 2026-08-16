package com.adam_stegienko.stacc_scheduler.dto;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * Accepts all common {@code LocalDateTime} serialisation formats that
 * cc-api-rest or any Spring Boot service may produce:
 * <ul>
 *   <li>{@code 2026-06-25T14:30:00}           (ISO, no fraction)</li>
 *   <li>{@code 2026-06-25T14:30:00.123456}     (ISO, with fraction)</li>
 *   <li>{@code 2026-06-25 14:30:00}            (space-separated)</li>
 *   <li>{@code 2026-06-25 14:30:00.123456}     (space-separated, with fraction)</li>
 * </ul>
 */
public class FlexibleLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    );

    public FlexibleLocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getText().trim();
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IOException("Cannot deserialize LocalDateTime from value '" + raw
                + "'. Supported formats: ISO-8601 (with or without fraction) "
                + "and 'yyyy-MM-dd HH:mm:ss' (with or without fraction).");
    }
}
