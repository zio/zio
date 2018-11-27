package scalaz.zio.internal.impls.padding;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import scalaz.zio.internal.MutableConcurrentQueue;

abstract class ClassFieldsPadding<A> extends MutableConcurrentQueue<A> {
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

abstract class HeadPadding<A> extends ClassFieldsPadding<A> {
    volatile long headCounter;
}

abstract class PreTailPadding<A> extends HeadPadding<A> {
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

abstract class TailPadding<A> extends PreTailPadding<A> {
    volatile long tailCounter;
}

public abstract class MutableQueueFieldsPadding<A> extends TailPadding<A> {
    public static final AtomicLongFieldUpdater<HeadPadding> head = AtomicLongFieldUpdater.newUpdater(HeadPadding.class, "headCounter");
    public static final AtomicLongFieldUpdater<TailPadding> tail = AtomicLongFieldUpdater.newUpdater(TailPadding.class, "tailCounter");

    long p200;
    long p202;
    long p203;
    long p204;
    long p205;
    long p206;
    long p207;
    long p208;
    long p209;
    long p210;
    long p211;
    long p212;
    long p213;
    long p214;
    long p215;
}
