package com.pointofdata.podos.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tag version history. Field and facet names are caller-defined.
 * Version order is storage timestamp, then tag number.
 */
public final class TagHistory {
    private TagHistory() {}

    /** One version index across the facets of a field. */
    public static final class TagSighting {
        public final int index;
        public final Map<String, TagOutput> facets = new LinkedHashMap<>();

        public TagSighting(int index) {
            this.index = index;
        }
    }

    /** One search term. Key is the full tag key; value may contain '='. */
    public static final class TagTerm {
        public final String key;
        public final String value;

        public TagTerm(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    /** Whether {@link com.pointofdata.podos.PodOsClient#appendTags} wrote a version. */
    public static final class AppendResult {
        public final boolean wrote;

        public AppendResult(boolean wrote) {
            this.wrote = wrote;
        }
    }

    private static boolean tagBefore(TagOutput a, TagOutput b) {
        String ats = a.timestamp == null ? "" : a.timestamp;
        String bts = b.timestamp == null ? "" : b.timestamp;
        int cmp = ats.compareTo(bts);
        if (cmp != 0) return cmp < 0;
        return a.tagNumber < b.tagNumber;
    }

    /** Latest row for each full tag key. Equal timestamp and tag number keep the later input. */
    public static Map<String, TagOutput> latestByKey(List<TagOutput> tags) {
        Map<String, TagOutput> out = new LinkedHashMap<>();
        if (tags == null) return out;
        for (TagOutput tag : tags) {
            TagOutput prev = out.get(tag.key);
            if (prev == null || !tagBefore(tag, prev)) {
                out.put(tag.key, tag);
            }
        }
        return out;
    }

    /**
     * Zip versions of keys that start with {@code field + "."}.
     * A facet added in a later batch is absent from earlier sightings.
     * An empty field returns no sightings.
     */
    public static List<TagSighting> fieldHistory(List<TagOutput> tags, String field) {
        List<TagSighting> empty = new ArrayList<>();
        if (field == null || field.isEmpty() || tags == null) return empty;
        String prefix = field + ".";
        Map<String, List<TagOutput>> groups = new LinkedHashMap<>();
        for (TagOutput tag : tags) {
            if (tag.key == null || !tag.key.startsWith(prefix) || tag.key.length() == prefix.length()) {
                continue;
            }
            String facet = tag.key.substring(prefix.length());
            groups.computeIfAbsent(facet, k -> new ArrayList<>()).add(tag);
        }
        if (groups.isEmpty()) return empty;
        int maxN = 0;
        for (List<TagOutput> rows : groups.values()) {
            rows.sort((a, b) -> tagBefore(a, b) ? -1 : tagBefore(b, a) ? 1 : 0);
            if (rows.size() > maxN) maxN = rows.size();
        }
        List<TagSighting> sightings = new ArrayList<>(maxN);
        for (int i = 0; i < maxN; i++) sightings.add(new TagSighting(i));
        for (Map.Entry<String, List<TagOutput>> entry : groups.entrySet()) {
            List<TagOutput> rows = entry.getValue();
            int offset = maxN - rows.size();
            for (int i = 0; i < rows.size(); i++) {
                sightings.get(offset + i).facets.put(entry.getKey(), rows.get(i));
            }
        }
        return sightings;
    }

    /** Classify a new value. appendTags does not call this. */
    public static String suggestDelta(boolean hasPrevious, String previous, String next) {
        if (!hasPrevious) return "initial";
        String prev = previous == null ? "" : previous;
        String nxt = next == null ? "" : next;
        if (prev.equals(nxt)) return "confirmed";
        return "modified";
    }

    /** One clause per term. The first line is boolean:or; later lines are boolean:and. */
    public static String tagSearchClauses(List<TagTerm> terms) {
        if (terms == null || terms.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) b.append('\n');
            TagTerm term = terms.get(i);
            String op = i == 0 ? "or" : "and";
            b.append("clause_type:S\tboolean:").append(op).append("\tlow:")
                    .append(term.key).append('=').append(term.value);
        }
        return b.toString();
    }
}
