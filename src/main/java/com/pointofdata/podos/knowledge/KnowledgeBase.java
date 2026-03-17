package com.pointofdata.podos.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides access to embedded Pod-OS knowledge documents for AI agents,
 * code assistants, and developer tooling.
 *
 * <p>Documents are bundled as classpath resources under
 * {@code knowledge/docs/} and are loaded on demand via
 * {@link Class#getResourceAsStream(String)}.
 *
 * <p>Mirrors Go's {@code knowledge} package, which uses {@code //go:embed}
 * to bake the same markdown files into the compiled binary.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // List all available document names
 * KnowledgeBase.listDocuments().forEach(System.out::println);
 *
 * // Retrieve a specific document
 * String doc = KnowledgeBase.getDocument("neural-memory")
 *                           .orElseThrow(() -> new IllegalArgumentException("not found"));
 * System.out.println(doc);
 * }</pre>
 */
public final class KnowledgeBase {

    // =========================================================================
    // Document name constants
    // =========================================================================

    /** Pod-OS Gateway network, Actor model, and connection overview. */
    public static final String DOC_COMMUNICATION        = "communication";

    /** Message structure, connection sequence, streaming modes, wire protocol. */
    public static final String DOC_MESSAGE_HANDLING     = "message-handling";

    /** Evolutionary Neural Memory event storage: StoreEvent, StoreBatchEvents, tags, links. */
    public static final String DOC_NEURAL_MEMORY        = "neural-memory";

    /** Evolutionary Neural Memory retrieval: GetEvent, GetEventsForTags, pattern search. */
    public static final String DOC_NEURAL_MEMORY_RETRIEVAL = "neural-memory-retrieval";

    private static final List<String> ALL_DOCUMENTS = Collections.unmodifiableList(Arrays.asList(
            DOC_COMMUNICATION,
            DOC_MESSAGE_HANDLING,
            DOC_NEURAL_MEMORY,
            DOC_NEURAL_MEMORY_RETRIEVAL
    ));

    private static final String RESOURCE_BASE = "/knowledge/docs/";

    private static final String[][] NAME_TO_FILE = {
        { DOC_COMMUNICATION,            "Pod-OS-Communication-Prompts.md"           },
        { DOC_MESSAGE_HANDLING,         "Pod-OS-Message-Handling-Prompts.md"        },
        { DOC_NEURAL_MEMORY,            "Pod-OS-Neural-Memory-Event-Prompts.md"     },
        { DOC_NEURAL_MEMORY_RETRIEVAL,  "Pod-OS-Neural-Memory-Retrieval-Prompts.md" },
    };

    private KnowledgeBase() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns the content of the named knowledge document, or
     * {@link Optional#empty()} if the name is not recognised.
     *
     * @param name one of the {@code DOC_*} constants or a raw document name
     * @return document content as a UTF-8 string
     * @throws KnowledgeBaseException if the resource exists in the registry but
     *                                cannot be read from the classpath
     */
    public static Optional<String> getDocument(String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        for (String[] entry : NAME_TO_FILE) {
            if (entry[0].equalsIgnoreCase(name)) {
                return Optional.of(loadResource(RESOURCE_BASE + entry[1]));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the content of the named document, throwing if not found.
     *
     * @param name document name
     * @return document content
     * @throws IllegalArgumentException if the name is not recognised
     * @throws KnowledgeBaseException   if the resource cannot be read
     */
    public static String requireDocument(String name) {
        return getDocument(name).orElseThrow(() ->
                new IllegalArgumentException("unknown knowledge document: '" + name
                        + "'. Available: " + ALL_DOCUMENTS));
    }

    /**
     * Returns an immutable list of all registered document names.
     *
     * @return list of document names in registration order
     */
    public static List<String> listDocuments() {
        return ALL_DOCUMENTS;
    }

    /**
     * Returns all documents concatenated into a single string, separated by
     * {@code "\n---\n"}. Useful for bulk injection into an LLM context window.
     *
     * @return all knowledge content as one string
     * @throws KnowledgeBaseException if any resource cannot be read
     */
    public static String allDocuments() {
        StringBuilder sb = new StringBuilder();
        for (String name : ALL_DOCUMENTS) {
            if (sb.length() > 0) sb.append("\n---\n");
            sb.append(requireDocument(name));
        }
        return sb.toString();
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private static String loadResource(String path) {
        try (InputStream is = KnowledgeBase.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new KnowledgeBaseException(
                        "knowledge resource not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new KnowledgeBaseException(
                    "failed to read knowledge resource: " + path, e);
        }
    }

    // =========================================================================
    // Exception
    // =========================================================================

    /**
     * Thrown when a knowledge document cannot be loaded from the classpath.
     */
    public static class KnowledgeBaseException extends RuntimeException {
        public KnowledgeBaseException(String message) {
            super(message);
        }
        public KnowledgeBaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
