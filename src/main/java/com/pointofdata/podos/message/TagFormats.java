package com.pointofdata.podos.message;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Helpers for the extended tag formats: GetEvent {@code tag_format=1} and
 * GetEventsForTags {@code buffer_format=1} (per-tag storage timestamps and owners).
 */
public final class TagFormats {

    /**
     * Event key Pod-OS reports as the owner of tags that have no owning event
     * (e.g. tags created under the $sys owner).
     */
    static final String NULL_OWNER_KEY_PREFIX = "+0000000000.000000";

    private static final String OWNER_MARKER = "\towner=";

    private TagFormats() {}

    /**
     * Parses one GetEvent response header field carrying a tag.
     * <pre>
     * tag_format=0: event_tag:nnnnnnnnn:fffffffff=key=value
     * tag_format=1: event_tag:nnnnnnnnn:fffffffff:ssssssssss.uuuuuu[:owner_id]=key=value
     * </pre>
     * The name is split into at most 4 pieces so owner IDs containing ':' stay intact.
     *
     * @return the parsed tag, or {@code null} when {@code name} is not a tag header
     */
    static TagOutput parseEventTagHeader(String name, String value) {
        if (name == null || !name.startsWith("event_tag:")) return null;
        String[] parts = name.substring("event_tag:".length()).split(":", 4);
        if (parts.length < 2) return null;

        TagOutput tag = new TagOutput();
        tag.frequency = 1;
        try { tag.tagNumber = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
        try { tag.frequency = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
        if (parts.length >= 3) tag.timestamp = parts[2];
        if (parts.length == 4) tag.owner = normalizeTagOwner(parts[3]);
        splitTagKeyValue(value, tag);
        return tag;
    }

    /** Orders GetEvent tags by their database tag counter (stable). */
    static void sortTagsByNumber(List<TagOutput> tags) {
        tags.sort(Comparator.comparingInt(t -> t.tagNumber));
    }

    /**
     * Splits a {@code key=value} tag string into {@code tag}. A string without a key keeps
     * the whole text as the value.
     */
    static void splitTagKeyValue(String s, TagOutput tag) {
        if (s == null) s = "";
        int eq = s.indexOf('=');
        if (eq > 0) {
            tag.key = s.substring(0, eq);
            tag.value = s.substring(eq + 1);
        } else {
            tag.key = "";
            tag.value = s;
        }
    }

    /** Maps Pod-OS "no owner" markers to an empty string. */
    static String normalizeTagOwner(String owner) {
        if (owner == null || owner.equals("NULL") || owner.startsWith(NULL_OWNER_KEY_PREFIX)) return "";
        return owner;
    }

    /**
     * Repairs {@code buffer_format=1} payloads requested with get_tag_owner or
     * get_tag_owner_unique_id. Pod-OS writes each tag's owner after the tag line's newline,
     * so it runs into the next record:
     * <pre>
     * _event_tag=K\t...\ttag_timestamp=T\n\towner=O_event_tag=K\t...
     * </pre>
     * Each owner is rejoined to the tag line it belongs to and the record break is restored.
     * Payloads already in the documented form (owner before the newline) are unchanged.
     */
    static String normalizeTagOwnerLines(String payload) {
        if (payload == null || !payload.contains("\n" + OWNER_MARKER)) return payload;
        payload = payload.replace("\n" + OWNER_MARKER, OWNER_MARKER);

        StringBuilder b = new StringBuilder(payload.length() + 64);
        int pos = 0;
        while (true) {
            int i = payload.indexOf(OWNER_MARKER, pos);
            if (i < 0) {
                b.append(payload, pos, payload.length());
                break;
            }
            int ownerStart = i + OWNER_MARKER.length();
            b.append(payload, pos, ownerStart);
            pos = ownerStart;

            int end = payload.length();
            int tab = payload.indexOf('\t', ownerStart);
            int nl = payload.indexOf('\n', ownerStart);
            if (tab >= 0) end = Math.min(end, tab);
            if (nl >= 0) end = Math.min(end, nl);
            int j = payload.substring(ownerStart, end).indexOf("_event_tag=");
            if (j >= 0) {
                b.append(payload, ownerStart, ownerStart + j).append('\n');
                pos = ownerStart + j;
            }
        }
        return b.toString();
    }

    /**
     * Parses a Pod-OS {@code "[+|-]ssssssssss.uuuuuu"} timestamp. The fraction is padded or
     * truncated to microseconds.
     */
    static Optional<Instant> parsePosixTimestamp(String s) {
        if (s == null || s.isEmpty()) return Optional.empty();
        boolean neg = false;
        if (s.charAt(0) == '+') {
            s = s.substring(1);
        } else if (s.charAt(0) == '-') {
            neg = true;
            s = s.substring(1);
        }
        int dot = s.indexOf('.');
        String secStr = dot >= 0 ? s.substring(0, dot) : s;
        String fracStr = dot >= 0 ? s.substring(dot + 1) : "";
        long sec;
        long usec = 0;
        try {
            sec = Long.parseLong(secStr);
            if (!fracStr.isEmpty()) {
                if (fracStr.length() > 6) fracStr = fracStr.substring(0, 6);
                StringBuilder f = new StringBuilder(fracStr);
                while (f.length() < 6) f.append('0');
                usec = Long.parseLong(f.toString());
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (sec < 0 || usec < 0) return Optional.empty();
        if (neg) {
            sec = -sec;
            usec = -usec;
        }
        return Optional.of(Instant.ofEpochSecond(sec, usec * 1000));
    }

    private static TagOwnerOutput tagOwnerOutputOf(Message msg) {
        if (msg.intent == null || msg.intent.name == null) return TagOwnerOutput.NONE;
        switch (msg.intent.name) {
            case "GetEvent": {
                GetEventOptions opts = msg.getEventOpts();
                return opts != null && opts.tagOwnerOutput != null ? opts.tagOwnerOutput : TagOwnerOutput.NONE;
            }
            case "GetEventsForTags": {
                GetEventsForTagsOptions opts = msg.getEventsForTagsOpts();
                return opts != null && opts.tagOwnerOutput != null ? opts.tagOwnerOutput : TagOwnerOutput.NONE;
            }
            default:
                return TagOwnerOutput.NONE;
        }
    }

    /**
     * Moves decoded tag owners from {@link TagOutput#owner} to {@link TagOutput#ownerUniqueId}
     * when {@code request} asked for owners by unique ID ({@link TagOwnerOutput#UNIQUE_ID}).
     * Pod-OS responses do not say which owner form they carry, so
     * {@link MessageDecoder#decodeMessage(byte[])} always fills {@code owner}. The client's
     * send methods apply this automatically; call it yourself when decoding raw responses.
     */
    public static void applyTagOwnerOutput(Message request, Message response) {
        if (request == null || response == null || tagOwnerOutputOf(request) != TagOwnerOutput.UNIQUE_ID) return;
        if (response.event != null) moveOwners(response.event.tags);
        if (response.response != null && response.response.eventRecords != null) {
            for (EventFields ev : response.response.eventRecords) {
                if (ev != null) moveOwners(ev.tags);
            }
        }
    }

    private static void moveOwners(List<TagOutput> tags) {
        if (tags == null) return;
        for (TagOutput tag : tags) {
            if (tag.owner != null && !tag.owner.isEmpty()) {
                tag.ownerUniqueId = tag.owner;
                tag.owner = "";
            }
        }
    }
}
