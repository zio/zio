package zio.internal;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * An unbounded MPSC (multi-producers single consumer) queue. This queue orders
 * elements FIFO (first-in-first-out).
 * 
 * <p>
 * The queue is thread-safe provided {@code poll} is called by the single
 * consumer (thread).
 * </p>
 * 
 * <p>
 * This implementation employs a wait free algorithm described in <a href=
 * "https://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue">
 * Non-intrusive MPSC node-based queue</a> by D. Vyukov.
 * </p>
 */
final public class Mailbox<A> implements Serializable {

	private transient Node read;
	@SuppressWarnings("unused")
	private transient volatile Node write;

	public Mailbox() {
		this.read = this.write = new Node(null);
	}

	/**
	 * Adds the specified element to the queue.
	 */
	public void add(A data) {
		Node next = new Node(data);
		Node prev = WRITE.getAndSet(this, next);
		NEXT.lazySet(prev, next);
	}

	/**
	 * Returns {@code true} if the queue has no elements. Otherwise, returns
	 * {@code false}.
	 */
	public boolean isEmpty() {
		return null == read.next;
	}

	/**
	 * Returns {@code true} if the queue has elements. Otherwise, returns
	 * {@code false}.
	 */
	public boolean nonEmpty() {
		return null != read.next;
	}

	/**
	 * Removes and returns the oldest element in the queue if the queue has
	 * elements. Otherwise, returns {@code null}.
	 */
	public A poll() {
		Node next = read.next;

		if (next == null)
			return null;

		@SuppressWarnings("unchecked")
		A data = (A) next.data;
		next.data = null;
		this.read = next;
		return data;
	}

	static class Node {
		Object data;
		volatile Node next;

		Node(Object data) {
			this.data = data;
		}
	}

	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<Mailbox, Node> WRITE = AtomicReferenceFieldUpdater
			.newUpdater(Mailbox.class, Node.class, "write");
	private static final AtomicReferenceFieldUpdater<Node, Node> NEXT = AtomicReferenceFieldUpdater
			.newUpdater(Node.class, Node.class, "next");
}