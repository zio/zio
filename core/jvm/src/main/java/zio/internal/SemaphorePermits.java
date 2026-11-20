/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * The free-permit counter of a {@code zio.Semaphore}, kept on a cache line of
 * its own.
 *
 * Every acquire and release writes this word, so whatever shares its line
 * (the waiter queue, the waiting flag) is invalidated with it, and once the
 * semaphore is promoted that layout is frozen for the life of the JVM. Padding
 * on both sides, as in {@link MutableQueueFieldsPadding}, keeps the counter
 * alone on its line and its prefetch-paired neighbour.
 *
 * The padding fields are {@code transient}: they hold no state, so they are
 * left out of the serialized form. That does not change the in-memory layout.
 */
public abstract class SemaphorePermits extends SemaphorePermitsPadding1 implements Serializable {
    private static final AtomicLongFieldUpdater<SemaphorePermitsValue> updater =
        AtomicLongFieldUpdater.newUpdater(SemaphorePermitsValue.class, "permitsValue");

    protected SemaphorePermits(long initial) {
        this.permitsValue = initial;
    }

    protected final long permitsGet() {
        return permitsValue;
    }

    protected final boolean permitsCompareAndSet(long expected, long updated) {
        return updater.compareAndSet(this, expected, updated);
    }

    protected final long permitsAddAndGet(long delta) {
        return updater.addAndGet(this, delta);
    }
}

abstract class SemaphorePermitsPadding0 implements Serializable {
    private transient long p000;
    private transient long p001;
    private transient long p002;
    private transient long p003;
    private transient long p004;
    private transient long p005;
    private transient long p006;
    private transient long p007;
    private transient long p008;
    private transient long p009;
    private transient long p010;
    private transient long p011;
    private transient long p012;
    private transient long p013;
    private transient long p014;
    private transient long p015;
}

abstract class SemaphorePermitsValue extends SemaphorePermitsPadding0 implements Serializable {
    volatile long permitsValue;
}

abstract class SemaphorePermitsPadding1 extends SemaphorePermitsValue implements Serializable {
    private transient long p100;
    private transient long p101;
    private transient long p102;
    private transient long p103;
    private transient long p104;
    private transient long p105;
    private transient long p106;
    private transient long p107;
    private transient long p108;
    private transient long p109;
    private transient long p110;
    private transient long p111;
    private transient long p112;
    private transient long p113;
    private transient long p114;
    private transient long p115;
}
