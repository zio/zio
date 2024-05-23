package zio.internal;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final public class Mailbox<A> implements Serializable {

	private transient Node read;
	@SuppressWarnings("unused")
	private transient volatile Node write;

	public Mailbox() {
		this.read = this.write = new Node(null);
	}

	public void add(A data) {
		if (data == null)
			throw new NullPointerException();

		Node next = new Node(data);

		AtomicReferenceFieldUpdater<Node, Node> NEXT = Mailbox.NEXT;

		Node prev = WRITE.getAndSet(this, next);
		NEXT.lazySet(prev, next);
	}

	public boolean isEmpty() {
		return null == read.next;
	}

	public boolean nonEmpty() {
		return null != read.next;
	}

	public A poll() {
		Node next = this.read.next;

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