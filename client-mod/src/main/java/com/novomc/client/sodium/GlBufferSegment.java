/*
 * Ported from Sodium 0.5 (CaffeineMC/sodium, mc1.20.1-0.5.11),
 * me.jellysquid.mods.sodium.client.gl.arena.GlBufferSegment.
 * Copyright (C) JellySquid and Sodium contributors. Modified for NovoMC.
 * Licensed under the GNU LGPL v3; see LICENSE-SODIUM.txt in this package.
 */
package com.novomc.client.sodium;

/** A run of vertices in a {@link GlBufferArena}, free or in use. Units are vertices, not bytes. */
public final class GlBufferSegment {
    private final GlBufferArena arena;
    private boolean free;
    private int offset;
    private int length;
    private GlBufferSegment next;
    private GlBufferSegment prev;

    GlBufferSegment(GlBufferArena arena, int offset, int length) {
        this.arena = arena;
        this.offset = offset;
        this.length = length;
    }

    public void delete() {
        arena.free(this);
    }

    int getEnd() {
        return offset + length;
    }

    public int getLength() {
        return length;
    }

    void setLength(int len) {
        if (len <= 0) {
            throw new IllegalArgumentException("len <= 0");
        }
        length = len;
    }

    public int getOffset() {
        return offset;
    }

    void setOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("start < 0");
        }
        this.offset = offset;
    }

    void setFree(boolean free) {
        this.free = free;
    }

    boolean isFree() {
        return free;
    }

    void setNext(GlBufferSegment next) {
        this.next = next;
    }

    GlBufferSegment getNext() {
        return next;
    }

    GlBufferSegment getPrev() {
        return prev;
    }

    void setPrev(GlBufferSegment prev) {
        this.prev = prev;
    }

    void mergeInto(GlBufferSegment entry) {
        setLength(length + entry.getLength());
        setNext(entry.getNext());
        if (next != null) {
            next.setPrev(this);
        }
    }
}
