/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A high-performance, Loom-friendly concurrent weak set specialized for Fiber.Runtime.
 * This implementation avoids global synchronization and carrier thread pinning
 * by using ConcurrentHashMap with specialized identity-based WeakKey wrappers.
 */
public final class FiberSet<A> extends AbstractSet<A> {
    private final ConcurrentHashMap<WeakKey<A>, Boolean> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<A> queue = new ReferenceQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    private static final class WeakKey<A> extends WeakReference<A> {
        private final int hashCode;

        WeakKey(A referent, ReferenceQueue<A> q) {
            super(referent, q);
            this.hashCode = System.identityHashCode(referent);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj instanceof WeakKey) {
                Object r1 = get();
                Object r2 = ((WeakKey<?>) obj).get();
                return r1 != null && r1 == r2;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private void expungeStaleEntries() {
        WeakKey<A> key;
        while ((key = (WeakKey<A>) queue.poll()) != null) {
            if (map.remove(key) != null) {
                size.decrementAndGet();
            }
        }
    }

    @Override
    public boolean add(A a) {
        if (a == null) throw new NullPointerException();
        expungeStaleEntries();
        if (map.putIfAbsent(new WeakKey<>(a, queue), Boolean.TRUE) == null) {
            size.incrementAndGet();
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object o) {
        if (o == null) return false;
        expungeStaleEntries();
        if (map.remove(new WeakKey<>((A) o, null)) != null) {
            size.decrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public int size() {
        expungeStaleEntries();
        return size.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        if (o == null) return false;
        return map.containsKey(new WeakKey<>((A) o, null));
    }

    @Override
    public Iterator<A> iterator() {
        expungeStaleEntries();
        final Iterator<WeakKey<A>> it = map.keySet().iterator();
        return new Iterator<A>() {
            private A next;

            @Override
            public boolean hasNext() {
                while (it.hasNext()) {
                    next = it.next().get();
                    if (next != null) return true;
                }
                return false;
            }

            @Override
            public A next() {
                if (next == null && !hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                A val = next;
                next = null;
                return val;
            }

            @Override
            public void remove() {
                it.remove();
                size.decrementAndGet();
            }
        };
    }

    @Override
    public void clear() {
        map.clear();
        size.set(0);
        while (queue.poll() != null);
    }
}
