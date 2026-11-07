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
    private long p000;
    private long p001;
    private long p002;
    private long p003;
    private long p004;
    private long p005;
    private long p006;
    private long p007;
    private long p008;
    private long p009;
    private long p010;
    private long p011;
    private long p012;
    private long p013;
    private long p014;
    private long p015;
}

abstract class SemaphorePermitsValue extends SemaphorePermitsPadding0 implements Serializable {
    volatile long permitsValue;
}

abstract class SemaphorePermitsPadding1 extends SemaphorePermitsValue implements Serializable {
    private long p100;
    private long p101;
    private long p102;
    private long p103;
    private long p104;
    private long p105;
    private long p106;
    private long p107;
    private long p108;
    private long p109;
    private long p110;
    private long p111;
    private long p112;
    private long p113;
    private long p114;
    private long p115;
}
