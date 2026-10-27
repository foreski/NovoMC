/*
 * Ported from Sodium 0.5 (CaffeineMC/sodium, mc1.20.1-0.5.11),
 * me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena.
 * Copyright (C) JellySquid and Sodium contributors. Modified for NovoMC:
 * LWJGL 2 calls instead of Sodium's CommandList, direct glBufferSubData
 * uploads instead of a staging buffer, and one upload at a time.
 * Licensed under the GNU LGPL v3; see LICENSE-SODIUM.txt in this package.
 */
package com.novomc.client.sodium;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

/**
 * One GL buffer shared by many chunk meshes. Meshes take best-fit free
 * segments; when none fits, the buffer grows and is compacted on the GPU
 * with glCopyBufferSubData, so the CPU never re-uploads old meshes.
 */
public final class GlBufferArena {
    private final int stride;
    private int buffer;
    private GlBufferSegment head;
    private int capacity;
    private int used;

    public GlBufferArena(int initialCapacity, int stride) {
        this.capacity = initialCapacity;
        this.stride = stride;
        head = new GlBufferSegment(this, 0, initialCapacity);
        head.setFree(true);
        buffer = createBuffer(capacity * stride);
    }

    private static int createBuffer(long bytes) {
        int id = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bytes, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return id;
    }

    /** The GL buffer name. It changes when the arena grows. */
    public int getBufferObject() {
        return buffer;
    }

    public boolean isEmpty() {
        return used <= 0;
    }

    public int getDeviceAllocatedMemory() {
        return capacity * stride;
    }

    /** Uploads {@code data} (whole vertices) into a new segment, growing the arena if needed. */
    public GlBufferSegment upload(ByteBuffer data) {
        int elements = data.remaining() / stride;
        GlBufferSegment dst = alloc(elements);
        if (dst == null) {
            ensureCapacity(elements);
            dst = alloc(elements);
            if (dst == null) {
                throw new IllegalStateException("Arena failed to grow");
            }
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) dst.getOffset() * stride, data);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return dst;
    }

    private void ensureCapacity(int elementCount) {
        // Resizing compacts, so all free space plus the growth becomes one segment.
        int needed = elementCount - (capacity - used);
        // Grow by half (Sodium grows by 1/16) since a 1.8.9 frame uploads few, large meshes.
        resize(Math.max(capacity + capacity / 2, capacity + needed));
    }

    private void resize(int newCapacity) {
        if (used > newCapacity) {
            throw new UnsupportedOperationException("New capacity must be larger than used size");
        }
        int tail = newCapacity - used;
        List<GlBufferSegment> usedSegments = getUsedSegments();
        List<int[]> copies = buildTransferList(usedSegments, tail);
        transferSegments(copies, newCapacity);

        head = new GlBufferSegment(this, 0, tail);
        head.setFree(true);
        if (usedSegments.isEmpty()) {
            head.setNext(null);
        } else {
            head.setNext(usedSegments.get(0));
            head.getNext().setPrev(head);
        }
    }

    /** Copy commands {readOffset, writeOffset, length}, merging runs that are already adjacent. */
    private static List<int[]> buildTransferList(List<GlBufferSegment> usedSegments, int base) {
        List<int[]> copies = new ArrayList<>();
        int[] current = null;
        int writeOffset = base;
        for (int i = 0; i < usedSegments.size(); i++) {
            GlBufferSegment s = usedSegments.get(i);
            if (current == null || current[0] + current[2] != s.getOffset()) {
                if (current != null) {
                    copies.add(current);
                }
                current = new int[] {s.getOffset(), writeOffset, s.getLength()};
            } else {
                current[2] += s.getLength();
            }
            s.setOffset(writeOffset);
            s.setNext(i + 1 < usedSegments.size() ? usedSegments.get(i + 1) : null);
            s.setPrev(i - 1 < 0 ? null : usedSegments.get(i - 1));
            writeOffset += s.getLength();
        }
        if (current != null) {
            copies.add(current);
        }
        return copies;
    }

    private void transferSegments(List<int[]> copies, int newCapacity) {
        int src = buffer;
        int dst = createBuffer((long) newCapacity * stride);
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, src);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, dst);
        for (int[] cmd : copies) {
            GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER,
                    (long) cmd[0] * stride, (long) cmd[1] * stride, (long) cmd[2] * stride);
        }
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        GL15.glDeleteBuffers(src);
        buffer = dst;
        capacity = newCapacity;
    }

    private List<GlBufferSegment> getUsedSegments() {
        List<GlBufferSegment> list = new ArrayList<>();
        for (GlBufferSegment seg = head; seg != null; seg = seg.getNext()) {
            if (!seg.isFree()) {
                list.add(seg);
            }
        }
        return list;
    }

    private GlBufferSegment alloc(int size) {
        GlBufferSegment a = findFree(size);
        if (a == null) {
            return null;
        }
        GlBufferSegment result;
        if (a.getLength() == size) {
            a.setFree(false);
            result = a;
        } else {
            GlBufferSegment b = new GlBufferSegment(this, a.getEnd() - size, size);
            b.setNext(a.getNext());
            b.setPrev(a);
            if (b.getNext() != null) {
                b.getNext().setPrev(b);
            }
            a.setLength(a.getLength() - size);
            a.setNext(b);
            result = b;
        }
        used += result.getLength();
        return result;
    }

    private GlBufferSegment findFree(int size) {
        GlBufferSegment best = null;
        for (GlBufferSegment entry = head; entry != null; entry = entry.getNext()) {
            if (entry.isFree()) {
                if (entry.getLength() == size) {
                    return entry;
                } else if (entry.getLength() >= size && (best == null || best.getLength() > entry.getLength())) {
                    best = entry;
                }
            }
        }
        return best;
    }

    void free(GlBufferSegment entry) {
        if (entry.isFree()) {
            throw new IllegalStateException("Already freed");
        }
        entry.setFree(true);
        used -= entry.getLength();

        GlBufferSegment next = entry.getNext();
        if (next != null && next.isFree()) {
            entry.mergeInto(next);
        }
        GlBufferSegment prev = entry.getPrev();
        if (prev != null && prev.isFree()) {
            prev.mergeInto(entry);
        }
    }

    public void delete() {
        GL15.glDeleteBuffers(buffer);
    }
}
