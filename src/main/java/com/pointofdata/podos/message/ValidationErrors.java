package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An immutable collection of {@link ValidationError} instances produced by
 * {@link MessageValidator}.
 *
 * <p>Provides two output formats:
 * <ul>
 *   <li>{@link #error()} — multi-line terminal-friendly engineer format</li>
 *   <li>{@link #llmJson()} — JSON array for LLM / AI agent prompt injection</li>
 * </ul>
 *
 * <p>An empty {@code ValidationErrors} signals "no problems found".
 */
public final class ValidationErrors implements Iterable<ValidationError> {

    private static final ValidationErrors EMPTY = new ValidationErrors(Collections.emptyList());

    private final List<ValidationError> errors;

    ValidationErrors(List<ValidationError> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static ValidationErrors empty() { return EMPTY; }

    public static ValidationErrors of(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) return EMPTY;
        return new ValidationErrors(errors);
    }

    public boolean isEmpty()           { return errors.isEmpty(); }
    public int size()                  { return errors.size(); }
    public ValidationError get(int i)  { return errors.get(i); }
    public List<ValidationError> list() { return errors; }

    @Override
    public Iterator<ValidationError> iterator() { return errors.iterator(); }

    public boolean hasErrors() {
        return errors.stream().anyMatch(ValidationError::isError);
    }

    public boolean hasWarnings() {
        return errors.stream().anyMatch(ValidationError::isWarn);
    }

    public List<ValidationError> errors() {
        return errors.stream().filter(ValidationError::isError).collect(Collectors.toList());
    }

    public List<ValidationError> warnings() {
        return errors.stream().filter(ValidationError::isWarn).collect(Collectors.toList());
    }

    /**
     * Engineer-friendly terminal format.
     * One block per error, empty string if no errors.
     */
    public String error() {
        if (errors.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ValidationError e : errors) {
            sb.append(e.toEngineerString());
        }
        return sb.toString();
    }

    /**
     * JSON array for LLM / AI-agent prompt injection.
     * Returns {@code "[]"} if no errors.
     */
    public String llmJson() {
        if (errors.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(errors.get(i).toJson());
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public String toString() { return error(); }
}
