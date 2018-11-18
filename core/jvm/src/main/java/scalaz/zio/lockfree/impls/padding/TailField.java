package scalaz.zio.lockfree.impls.padding;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

abstract class PadHeadField<A> extends HeadField<A> {
    long p100;
    long p102;
    long p103;
    long p104;
    long p105;
    long p106;
    long p107;
    long p108;
    long p109;
    long p110;
    long p111;
    long p112;
    long p113;
    long p114;
    long p115;
}

public abstract class TailField<A> extends PadHeadField<A> {
    public static final AtomicLongFieldUpdater<TailField> tail = AtomicLongFieldUpdater.newUpdater(TailField.class, "tailCounter");
    volatile long tailCounter;
}
