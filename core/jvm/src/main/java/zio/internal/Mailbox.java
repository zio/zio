package zio.internal;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An unbounded MPSC (multi-producers single consumer) queue that orders
 * elements FIFO (first-in-first-out).
 *
 * @apiNote The queue is thread-safe provided {@code poll} is invoked by the
 *          single consumer (thread).
 * 
 * @implNote The implementation employs an algorithm described in <a href=
 *           "https://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue">
 *           Non-intrusive MPSC node-based queue</a> by D. Vyukov.
 */
final class Mailbox<A> implements Serializable {

	private transient Node<A> read;
	@SuppressWarnings("unused")
	private transient volatile Node<A> write;

	Mailbox() {
		read = write = new Node<A>(null);
	}

	/**
	 * Adds the specified element to the queue.
	 * 
	 * @apiNote The element MUST not be {@code null}.
	 */
	public void add(A data) {
		Node<A> next = new Node<A>(data);
		NEXT.setRelease(WRITE.getAndSet(this, next), next);
	}

	/**
	 * Returns {@code true} if the queue has no elements. Otherwise, returns
	 * {@code false}.
	 * 
	 * @apiNote This method SHOULD be invoked by the single consumer (thread).
	 * 
	 * @apiNote This method can return a false negative at some point if elements
	 *          were added using `prepend`. Check the value returned by `poll` in
	 *          such cases.
	 */
	public boolean isEmpty() {
		return null == NEXT.getAcquire(read);
	}

	/**
	 * Returns {@code true} if the queue has elements. Otherwise, returns
	 * {@code false}.
	 * 
	 * @apiNote This method SHOULD be invoked by the single consumer (thread).
	 *
	 * @apiNote This method can return a false positive at some point if elements
	 *          were added using `prepend`. Check the value returned by `poll` in
	 *          such cases.
	 */
	public boolean nonEmpty() {
		return null != NEXT.getAcquire(read);
	}

	/**
	 * Removes and returns the oldest element in the queue if the queue has
	 * elements. Otherwise, returns {@code null}.
	 * 
	 * @apiNote This method MUST be invoked by the single consumer (thread).
	 */
	public A poll() {
		final Node<A> next = (Node<A>) (NEXT.get(read));

		if (next == null)
			// queue is empty
			return null;

		A data = next.data;
		this.read = next;

		if (null != data) {
			next.data = null;
			return data;
		}

		// skip phantom node retained by prepend
		return poll();
	}

	/**
	 * Adds the specified element to the beginning of the queue; the element will be
	 * read before any other element in the queue.
	 *
	 * @apiNote This method MUST be invoked by the single consumer (thread).
	 * 
	 * @apiNote The element MUST not be {@code null}.
	 */
	public void prepend(A data) {
		// optimization: avoid atomic operation by retaining (phantom) read node
		read = new Node<A>(null, new Node<A>(data, read));
	}

	/**
	 * A version of `prepend` that adds 2 elements to the beginning of the queue.
	 *
	 * Calling this method is equivalent to running
	 * 
	 * <pre>
	 * mailbox.prepend(data2);
	 * mailbox.prepend(data1);
	 * </pre>
	 *
	 * @apiNote This method MUST be invoked by the single consumer (thread).
	 * 
	 * @apiNote The elements MUST not be {@code null}.
	 */
	public void prepend2(A data1, A data2) {
		// optimization: avoid atomic operation by retaining (phantom) read node
		read = new Node<A>(null, new Node<A>(data1, new Node<A>(data2, read)));
	}

	static final class Node<A> implements Serializable {
		A data;
		transient volatile Node<A> next;

		Node(A data) {
			this.data = data;
		}

		Node(A data, Node<A> next) {
			this.data = data;
			this.next = next;
		}
	}

	private static final VarHandle NEXT;
	private static final VarHandle WRITE;

	static {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			NEXT = MethodHandles.privateLookupIn(Node.class, lookup).findVarHandle(Node.class, "next", Node.class);
			WRITE = MethodHandles.privateLookupIn(Mailbox.class, lookup).findVarHandle(Mailbox.class, "write",
					Node.class);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
