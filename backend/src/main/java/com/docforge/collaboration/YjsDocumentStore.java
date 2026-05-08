package com.docforge.collaboration;

/**
 * Manages per-document Yjs binary state:
 * — in-memory cache for active documents
 * — durable storage in MongoDB (via DocumentService)
 * — debounced persistence (5 s) to avoid excessive writes
 *
 * One implementation ({@link YjsDocumentStoreImpl}) handles a single server instance.
 * Future Redis-backed implementation can replace it for multi-instance deployments.
 */
public interface YjsDocumentStore {

    /**
     * Returns the ordered list of individual Yjs updates stored for the document.
     * If not in the in-memory cache, loads from MongoDB.
     * Returns an empty list for documents with no state yet.
     *
     * Each element is a raw Yjs update binary (not concatenated).
     * New clients must receive each update as a separate SYNC_UPDATE frame.
     */
    java.util.List<byte[]> getUpdates(String docId);

    /**
     * Appends the incoming Yjs update to the stored list.
     */
    void applyUpdate(String docId, byte[] update);

    /**
     * Schedules a debounced persist to MongoDB (5 s after last call).
     * Subsequent calls within the window reset the timer.
     */
    void schedulePersist(String docId);

    /**
     * Immediately persists the current in-memory state to MongoDB.
     * Called when the last participant disconnects so no state is lost.
     */
    void persistNow(String docId);

    /**
     * Removes the document from the in-memory cache after the last session closes.
     * Must be called after {@link #persistNow(String)}.
     */
    void evict(String docId);
}

