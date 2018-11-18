package scalaz.zio.lockfree.impls.padding;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import scalaz.zio.lockfree.MutableConcurrentQueue;

abstract class PadClassFields<A> extends MutableConcurrentQueue<A> {
    int p000;
    long p002;
    long p003;
    long p004;
    long p005;
    long p006;
    long p007;
    long p008;
    long p009;
    long p010;
    long p011;
    long p012;
    long p013;
    long p014;
    long p015;
}

public abstract class HeadField<A> extends PadClassFields<A> {
    public static final AtomicLongFieldUpdater<HeadField> head = AtomicLongFieldUpdater.newUpdater(HeadField.class, "headCounter");
    volatile long headCounter;
}
