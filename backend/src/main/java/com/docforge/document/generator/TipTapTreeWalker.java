package com.docforge.document.generator;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Recursively transforms a TipTap JSON document tree stored as
 * {@code Map<String, Object>}.
 *
 * <p>The tree structure mirrors ProseMirror/TipTap JSON:
 * <pre>
 * {
 *   "type": "doc",
 *   "content": [
 *     { "type": "paragraph", "content": [
 *         { "type": "text", "text": "Hello" },
 *         { "type": "parameterField", "attrs": { "parameterCode": "..." } }
 *     ]},
 *     { "type": "conditionalBlock", "attrs": { "condition": {...} },
 *       "content": [ ... ] }
 *   ]
 * }
 * </pre>
 *
 * <p>The {@link NodeTransformer} receives each child node and returns:
 * <ul>
 *   <li>{@code []} — delete the node</li>
 *   <li>{@code [node]} — keep (or replace with a single node)</li>
 *   <li>{@code [a, b, ...]} — expand (e.g. unwrap a conditional block)</li>
 * </ul>
 *
 * <p>The walker recurses into replacement nodes, so unwrapped content is
 * further processed in the same pass.
 */
@Component
public class TipTapTreeWalker {

    /** Functional interface for a node-level transformation. */
    @FunctionalInterface
    public interface NodeTransformer
            extends Function<Map<String, Object>, List<Map<String, Object>>> {}

    /**
     * Transforms the document tree rooted at {@code root}.
     *
     * @param root        TipTap "doc" node (or any subtree root)
     * @param transformer called for every child node in the tree
     * @return a new tree with transformations applied (original is not mutated)
     */
    public Map<String, Object> transform(Map<String, Object> root, NodeTransformer transformer) {
        if (root == null) return null;
        return processNode(root, transformer);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> processNode(
            Map<String, Object> node,
            NodeTransformer transformer) {

        Object rawContent = node.get("content");
        if (!(rawContent instanceof List<?> contentList) || contentList.isEmpty()) {
            return node;
        }

        List<Object> newContent = new ArrayList<>();
        for (Object item : contentList) {
            if (!(item instanceof Map<?, ?> childRaw)) continue;
            Map<String, Object> child = (Map<String, Object>) childRaw;

            // Let the transformer decide what to do with this child
            List<Map<String, Object>> replacements = transformer.apply(child);

            // Recurse into each replacement
            for (Map<String, Object> replacement : replacements) {
                newContent.add(processNode(replacement, transformer));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>(node);
        result.put("content", newContent);
        return result;
    }
}
