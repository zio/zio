package zio.internal;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An unbounded MPSC (multi-producers single consumer) queue that orders
 * elements FIFO (first-in-first-out).
 *
 * @apiNote The queue is thread-safe provided {@code poll} is invoked by the
 *          single consumer (thread).
 * 
 * @apiNote The class extends {@code AtomicReference} to improve performance;
 *          calling methods in the super class that mutate the internal state is
 *          not permitted.
 * 
 * @implNote The implementation employs an algorithm described in <a href=
 *           "https://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue">
 *           Non-intrusive MPSC node-based queue</a> by D. Vyukov.
 */
final class Mailbox<A> extends AtomicReference<Mailbox.Node<A>> {

	private transient Node<A> read;

	Mailbox() {
		super(new Node<A>(null));
		read = getPlain();
	}

	/**
	 * Adds the specified element to the queue.
	 * 
	 * @throws NullPointerException if element is {@code null}
	 */
	public void add(A data) throws NullPointerException {
		if (null == data)
			throw new NullPointerException();
		Node<A> next = new Node<A>(data);
		Node<A> prev = getAndSet(next);
		prev.lazySet(next);
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
		return null == read.get();
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
		return null != read.get();
	}

	/**
	 * Adds the specified element to the beginning of the queue; the element will be
	 * read before any other element in the queue.
	 *
	 * @apiNote This method MUST be invoked by the single consumer (thread).
	 * 
	 * @throws NullPointerException if element is {@code null}
	 */
	public void prepend(A data) throws NullPointerException {
		if (null == data)
			throw new NullPointerException();
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
	 * @throws NullPointerException if either element is {@code null}
	 */
	public void prepend2(A data1, A data2) throws NullPointerException {
		if (null == data1 || null == data2)
			throw new NullPointerException();
		// optimization: avoid atomic operation by retaining (phantom) read node
		read = new Node<A>(null, new Node<A>(data1, new Node<A>(data2, read)));
	}

	/**
	 * Removes and returns the oldest element in the queue if the queue has
	 * elements. Otherwise, returns {@code null}.
	 * 
	 * @apiNote This method MUST be invoked by the single consumer (thread).
	 */
	public A poll() {
		Node<A> next;
		A data;
		while (true) {
			next = read.getPlain();

			if (next == null)
				return null; // queue is empty

			data = next.data;
			this.read = next;

			if (null == data)
				continue; // skip phantom node retained by prepend

			next.data = null;
			return data;
		}
	}

	static class Node<A> extends AtomicReference<Node<A>> {
		A data;

		Node(A data) {
			this.data = data;
		}

		Node(A data, Node<A> next) {
			super(next);
			this.data = data;
		}
	}

}
