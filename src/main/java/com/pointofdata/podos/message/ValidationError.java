package com.pointofdata.podos.message;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single structured validation error produced by {@link MessageValidator}.
 *
 * <p>Every error is designed for two audiences:
 * <ul>
 *   <li><b>Engineers</b> — {@link #toEngineerString()} gives a terminal-friendly one-liner.</li>
 *   <li><b>LLMs / AI agents</b> — {@link #toJson()} gives a JSON object for prompt injection.</li>
 * </ul>
 */
public final class ValidationError {

    public static final String SEVERITY_ERROR = "error";
    public static final String SEVERITY_WARN  = "warn";

    public static final String RULE_REQUIRED         = "required";
    public static final String RULE_ONE_OF_REQUIRED  = "one_of_required";
    public static final String RULE_FORMAT           = "format";
    public static final String RULE_NIL_STRUCT       = "nil_struct";
    public static final String RULE_HEADER_MISSING   = "header_missing";
    public static final String RULE_HEADER_VALUE     = "header_value";
    public static final String RULE_PAYLOAD_TYPE     = "payload_type";
    public static final String RULE_PAYLOAD_FORMAT   = "payload_format";
    public static final String RULE_UNCOVERED        = "uncovered";

    private final String severity;
    private final String intent;
    private final String field;
    private final String wireField;
    private final String rule;
    private final String message;
    private final String fix;
    private final String exampleCode;
    private final List<String> references;

    private ValidationError(Builder b) {
        this.severity    = b.severity;
        this.intent      = b.intent;
        this.field       = b.field;
        this.wireField   = b.wireField;
        this.rule        = b.rule;
        this.message     = b.message;
        this.fix         = b.fix;
        this.exampleCode = b.exampleCode;
        this.references  = b.references != null
                ? Collections.unmodifiableList(b.references)
                : Collections.emptyList();
    }

    public String severity()    { return severity; }
    public String intent()      { return intent; }
    public String field()       { return field; }
    public String wireField()   { return wireField; }
    public String rule()        { return rule; }
    public String message()     { return message; }
    public String fix()         { return fix; }
    public String exampleCode() { return exampleCode; }
    public List<String> references() { return references; }

    public boolean isError() { return SEVERITY_ERROR.equals(severity); }
    public boolean isWarn()  { return SEVERITY_WARN.equals(severity); }

    /**
     * Terminal-friendly engineer format.
     *
     * <pre>
     * [ERROR] LinkEvent / NeuralMemory.Link.Category (category): required
     *   What: Category is required for LinkEvent and is missing.
     *   Fix:  Set NeuralMemory.Link.Category to a non-empty string (e.g. "related").
     *   Code: msg.neuralMemory.link.category = "related"
     * </pre>
     */
    public String toEngineerString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(severity.toUpperCase()).append("] ");
        sb.append(intent).append(" / ").append(field);
        if (wireField != null && !wireField.isEmpty()) {
            sb.append(" (").append(wireField).append(')');
        }
        sb.append(": ").append(rule).append('\n');
        sb.append("  What: ").append(message).append('\n');
        sb.append("  Fix:  ").append(fix).append('\n');
        if (exampleCode != null && !exampleCode.isEmpty()) {
            sb.append("  Code: ").append(exampleCode).append('\n');
        }
        return sb.toString();
    }

    /**
     * JSON object for LLM injection. Minimal, dependency-free serialization.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendJsonField(sb, "severity",    severity, true);
        appendJsonField(sb, "intent",      intent, false);
        appendJsonField(sb, "struct_path", field, false);
        appendJsonField(sb, "wire_field",  wireField, false);
        appendJsonField(sb, "rule",        rule, false);
        appendJsonField(sb, "description", message, false);
        appendJsonField(sb, "fix",         fix, false);
        appendJsonField(sb, "example_code", exampleCode, false);
        sb.append(",\"references\":[");
        for (int i = 0; i < references.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escapeJson(references.get(i))).append('"');
        }
        sb.append("]}");
        return sb.toString();
    }

    @Override
    public String toString() { return toEngineerString(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidationError)) return false;
        ValidationError that = (ValidationError) o;
        return Objects.equals(severity, that.severity)
                && Objects.equals(intent, that.intent)
                && Objects.equals(field, that.field)
                && Objects.equals(rule, that.rule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, intent, field, rule);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String severity = SEVERITY_ERROR;
        private String intent = "";
        private String field = "";
        private String wireField = "";
        private String rule = "";
        private String message = "";
        private String fix = "";
        private String exampleCode = "";
        private List<String> references;

        private Builder() {}

        public Builder severity(String v)    { this.severity = v; return this; }
        public Builder intent(String v)      { this.intent = v; return this; }
        public Builder field(String v)       { this.field = v; return this; }
        public Builder wireField(String v)   { this.wireField = v; return this; }
        public Builder rule(String v)        { this.rule = v; return this; }
        public Builder message(String v)     { this.message = v; return this; }
        public Builder fix(String v)         { this.fix = v; return this; }
        public Builder exampleCode(String v) { this.exampleCode = v; return this; }
        public Builder references(String... refs) { this.references = Arrays.asList(refs); return this; }
        public Builder references(List<String> refs) { this.references = refs; return this; }

        public ValidationError build() { return new ValidationError(this); }
    }

    // =========================================================================
    // JSON helpers (zero-dependency)
    // =========================================================================

    private static void appendJsonField(StringBuilder sb, String key, String value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(key).append("\":\"").append(escapeJson(value != null ? value : "")).append('"');
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
}
