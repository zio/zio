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

    protected long p200;
    protected long p202;
    protected long p203;
    protected long p204;
    protected long p205;
    protected long p206;
    protected long p207;
    protected long p208;
    protected long p209;
    protected long p210;
    protected long p211;
    protected long p212;
    protected long p213;
    protected long p214;
    protected long p215;
}

// Aux classes below

abstract class ClassFieldsPadding<A> extends MutableConcurrentQueue<A> {
    protected int p000;
    protected long p002;
    protected long p003;
    protected long p004;
    protected long p005;
    protected long p006;
    protected long p007;
    protected long p008;
    protected long p009;
    protected long p010;
    protected long p011;
    protected long p012;
    protected long p013;
    protected long p014;
    protected long p015;
}

abstract class HeadPadding<A> extends ClassFieldsPadding<A> {
    protected volatile long headCounter;
}

abstract class PreTailPadding<A> extends HeadPadding<A> {
    protected long p100;
    protected long p102;
    protected long p103;
    protected long p104;
    protected long p105;
    protected long p106;
    protected long p107;
    protected long p108;
    protected long p109;
    protected long p110;
    protected long p111;
    protected long p112;
    protected long p113;
    protected long p114;
    protected long p115;
}

abstract class TailPadding<A> extends PreTailPadding<A> {
    protected volatile long tailCounter;
}
