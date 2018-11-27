package scalaz.zio.internal.impls.padding;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import scalaz.zio.internal.MutableConcurrentQueue;

/*
 * The classes below provide padding for contended fields in the
 * [[MutableConcurrentQueue]] layout, specifically `head` and `tail`.
 *
 * The layout of a Java object in memory is dynamic and is under
 * control of the JVM which can shuffle fields to optimize the
 * object's footprint. However, when the object is used in a
 * concurrent setting having all fields densely packed may affect
 * performance since certain concurrently modified fields can fall on
 * the same cache-line and be subject to _False Sharing_. In such case
 * we would like to space out _hot_ fields and thus make access more
 * cache-friendly.
 *
 * On x86 one cache-line is 64KiB and we'd need to space out fields by
 * at least *2* cache-lines because modern processors do pre-fetching,
 * i.e. get the requested cache-line and the one after. Thus we need
 * ~128KiB in-between hot fields.
 *
 * One solution could be just adding a bunch of long fields, but if
 * those are defined within the same class as `head` or `tail`, JVM
 * will likely shuffle the longs and mess up the padding.
 *
 * There is a workaround that relies on the fact the fields of a
 * super-class go before fields of a sub-class in the object's
 * layout. So, a properly crafted inheritance chain can guarantee
 * spacing for the hot fields.
 *
 * To illustrate the effect of such padding, here's an output by JOL
 * (truncated):
 *
 * scalaz.zio.internal.impls.padding.MutableQueueFieldsPadding
 * OFFSET  SIZE   TYPE DESCRIPTION
 *      0    12        (object header)
 *     12     4    int ClassFieldsPadding.p000
 *     16     8   long ClassFieldsPadding.p002
 *    ........................................
 *    120     8   long ClassFieldsPadding.p015
 *    128     8   long HeadPadding.headCounter
 *    136     8   long PreTailPadding.p100
 *    144     8   long PreTailPadding.p102
 *    ...................................
 *    248     8   long PreTailPadding.p115
 *    256     8   long TailPadding.tailCounter
 *    264     8   long MutableQueueFieldsPadding.p200
 *    272     8   long MutableQueueFieldsPadding.p202
 *    ...............................................
 *    376     8   long MutableQueueFieldsPadding.p215
 * Instance size: 384 bytes
 * Space losses: 0 bytes internal + 0 bytes external = 0 bytes total
 */
public abstract class MutableQueueFieldsPadding<A> extends TailPadding<A> {
    public static final AtomicLongFieldUpdater<HeadPadding> headUpdater = AtomicLongFieldUpdater.newUpdater(HeadPadding.class, "headCounter");
    public static final AtomicLongFieldUpdater<TailPadding> tailUpdater = AtomicLongFieldUpdater.newUpdater(TailPadding.class, "tailCounter");

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

// Aux classes below

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
