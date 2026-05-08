package com.docforge.collaboration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Helpers for the lib0 binary encoding used by y-websocket / Yjs.
 *
 * Encoding: unsigned varint — 7 bits per byte, LSB first, high bit = "more bytes follow".
 * VarUint8Array: varint length prefix followed by raw bytes.
 *
 * y-websocket v3 outer message types:
 *   0 = messageSync      — sync sub-protocol (step1 / step2 / update inside)
 *   1 = messageAwareness — awareness (cursors, presence)
 *   2 = messageAuth
 *   3 = messageQueryAwareness
 *
 * Sync sub-types (inside messageSync):
 *   0 = syncStep1  — state vector
 *   1 = syncStep2  — update (response to step1)
 *   2 = update     — incremental update
 */
final class Varint {

    // ── Outer message types ───────────────────────────────────────────────────
    static final int MSG_SYNC       = 0;
    static final int MSG_AWARENESS  = 1;

    // ── Sync sub-types ────────────────────────────────────────────────────────
    static final int SYNC_STEP1 = 0;
    static final int SYNC_STEP2 = 1;
    static final int SYNC_UPDATE = 2;

    private Varint() {}

    // ── Read ──────────────────────────────────────────────────────────────────

    static int readVarUint(ByteBuffer buf) {
        int result = 0, shift = 0;
        while (buf.hasRemaining()) {
            int b = buf.get() & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
        }
        throw new IllegalStateException("Buffer underflow while reading varint");
    }

    static byte[] readVarUint8Array(ByteBuffer buf) {
        int len = readVarUint(buf);
        if (len < 0 || len > buf.remaining()) {
            throw new IllegalStateException("Invalid varUint8Array length: " + len);
        }
        byte[] result = new byte[len];
        buf.get(result);
        return result;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    static void writeVarUint(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static void writeVarUint8Array(OutputStream out, byte[] data) throws IOException {
        writeVarUint(out, data.length);
        out.write(data);
    }

    // ── Message builders ──────────────────────────────────────────────────────

    /**
     * Builds: [MSG_SYNC][SYNC_STEP1][stateVector as varUint8Array]
     * Empty state vector = [0] (zero entries in the map) → asks the peer for all updates.
     */
    static byte[] encodeSyncStep1() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4);
        writeVarUint(out, MSG_SYNC);
        writeVarUint(out, SYNC_STEP1);
        // empty state vector: a single varint 0 (zero client entries)
        writeVarUint8Array(out, new byte[]{0});
        return out.toByteArray();
    }

    /**
     * Builds: [MSG_SYNC][SYNC_STEP2][update as varUint8Array]
     * Sends accumulated updates in response to a syncStep1.
     * If update is null or empty, sends an empty update payload.
     */
    static byte[] encodeSyncStep2(byte[] update) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarUint(out, MSG_SYNC);
        writeVarUint(out, SYNC_STEP2);
        writeVarUint8Array(out, update != null ? update : new byte[0]);
        return out.toByteArray();
    }

    /**
     * Builds: [MSG_SYNC][SYNC_UPDATE][update as varUint8Array]
     * Used when rebroadcasting a syncStep2's update to other peers as a plain update.
     */
    static byte[] encodeUpdate(byte[] update) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarUint(out, MSG_SYNC);
        writeVarUint(out, SYNC_UPDATE);
        writeVarUint8Array(out, update);
        return out.toByteArray();
    }
}
