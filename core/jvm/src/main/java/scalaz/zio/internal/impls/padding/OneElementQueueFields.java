package scalaz.zio.internal.impls.padding;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import scalaz.zio.internal.MutableConcurrentQueue;

public abstract class OneElementQueueFields<A> extends MutableConcurrentQueue<A> implements Serializable {
    public static final AtomicReferenceFieldUpdater<OneElementQueueFields, Object> refUpdater =
        AtomicReferenceFieldUpdater.newUpdater(OneElementQueueFields.class, Object.class, "ref");

    public static final AtomicLongFieldUpdater<OneElementQueueFields> headUpdater =
        AtomicLongFieldUpdater.newUpdater(OneElementQueueFields.class, "headCounter");

    public static final AtomicLongFieldUpdater<OneElementQueueFields> tailUpdater =
        AtomicLongFieldUpdater.newUpdater(OneElementQueueFields.class, "tailCounter");

    protected volatile Object ref;
    protected volatile long headCounter;
    protected volatile long tailCounter;
}
