/*
 * Copyright 2022-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package zio.internal;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-consumer, multi-producer mailbox for one fiber.
 *
 * <p>The hot state is this object itself (it extends {@link AtomicLong}), so an
 * idle fiber pays for one object and no queue node. {@code seq} is even when
 * the mailbox is empty and odd when exactly one message is parked in {@code
 * slot}. The odd value is also a generation, so a reused message (the
 * {@code resumeUnit} singleton) cannot be confused with an older offer.
 *
 * <p>A second concurrent offer promotes the mailbox, once, to a Vyukov MPSC
 * linked queue. Promotion publishes an already-linked chain, so the two
 * messages that caused it stay in order, and every later offer appends to that
 * queue. The mailbox never returns to the slot, which is what keeps a new
 * offer from jumping ahead of messages already in the overflow.
 *
 * <p>{@code pollRelaxed} may miss a message whose offer has not finished
 * publishing. {@code poll} waits that publication out. {@link #isEmpty()} is
 * the precise check: an offer that has already bumped {@code seq}, and a
 * producer that has swapped the overflow tail but not linked the node yet,
 * both count as non-empty. That is the property the fiber needs before it
 * parks or publishes its exit.
 *
 * <p>Internal runtime structure. Not part of the supported ZIO API. The
 * inherited {@code AtomicLong} operations are the sequence counter; callers
 * must not use them.
 */
public final class FiberMailbox extends AtomicLong {
  static final long PROMOTED = Long.MIN_VALUE;

  /**
   * Generation of the message currently stored in {@code slot}. Written after
   * {@code slot} with a volatile store, so a consumer that observes this value
   * equal to {@code seq} also observes that message.
   */
  private volatile long slotSeq;

  /** Plain payload. Published by the subsequent volatile write to {@code slotSeq}. */
  private FiberMessage slot;

  /** Non-null only after {@code seq} has been set to {@link #PROMOTED}. */
  private volatile Overflow overflow;

  /**
   * Consumer-only cache of {@link #overflow}. Producers never read it. After
   * promotion the run loop polls this and skips the sequence counter.
   */
  private Overflow overflowCache;

  public FiberMailbox() {
    super(0L);
  }

  /**
   * Enqueue {@code message}. Wait-free on the empty mailbox (one CAS, no
   * allocation). A second message allocates the overflow queue.
   */
  public void add(final FiberMessage message) {
    final long s = get();
    if (s == PROMOTED) {
      promoted().add(message);
      return;
    }
    if ((s & 1L) == 0L && compareAndSet(s, s + 1L)) {
      // The CAS publishes the generation. The payload is published by slotSeq,
      // which is written after slot, so pollers that require slotSeq == seq
      // cannot observe a stale payload.
      slot = message;
      slotSeq = s + 1L;
      return;
    }
    addSlow(message);
  }

  private void addSlow(final FiberMessage message) {
    for (; ; ) {
      final long s = get();
      if (s == PROMOTED) {
        promoted().add(message);
        return;
      }
      if ((s & 1L) == 0L) {
        if (compareAndSet(s, s + 1L)) {
          slot = message;
          slotSeq = s + 1L;
          return;
        }
        continue;
      }
      // Another producer owns this odd generation and may not have published
      // the payload yet. Spinning here is the same short window as a Vyukov
      // bubble: the owner is between two stores.
      if (slotSeq != s) {
        Thread.onSpinWait();
        continue;
      }
      final FiberMessage first = slot;
      final Overflow queue = Overflow.build(first, message);
      if (compareAndSet(s, PROMOTED)) {
        // Published after the CAS so a poller that observes PROMOTED either
        // sees this queue or spins until the store lands. Exactly one promoter
        // wins the CAS; losers retry and append to this queue, which preserves
        // order (slot message, winner, then later offers).
        overflow = queue;
        return;
      }
    }
  }

  /**
   * Remove the next message, or return null if the consumer cannot yet see
   * one. Does not spin. Safe to call on every step of the run loop.
   */
  public FiberMessage pollRelaxed() {
    final Overflow cached = overflowCache;
    if (cached != null) return cached.pollRelaxed();
    final long s = get();
    if ((s & 1L) == 0L) {
      if (s != PROMOTED) return null;
      final Overflow queue = promoted();
      overflowCache = queue;
      return queue.pollRelaxed();
    }
    if (slotSeq != s) return null;
    final FiberMessage message = slot;
    if (compareAndSet(s, s + 1L)) return message;
    // Promotion won the generation. The message is in the overflow queue.
    return pollRelaxed();
  }

  /**
   * Remove the next message. If a producer has claimed a slot but not
   * published it, spins until that store is visible. Returns null only when
   * the mailbox is actually empty.
   */
  public FiberMessage poll() {
    final Overflow cached = overflowCache;
    if (cached != null) return cached.poll();
    for (; ; ) {
      final long s = get();
      if (s == PROMOTED) {
        final Overflow queue = promoted();
        overflowCache = queue;
        return queue.poll();
      }
      if ((s & 1L) == 0L) return null;
      if (slotSeq == s) {
        final FiberMessage message = slot;
        if (compareAndSet(s, s + 1L)) return message;
        continue;
      }
      Thread.onSpinWait();
    }
  }

  /**
   * True only when no offer has linearized and no overflow node is waiting.
   * An in-flight offer (sequence already odd, payload not stored, or overflow
   * tail swapped but not linked) returns false.
   */
  public boolean isEmpty() {
    final Overflow cached = overflowCache;
    if (cached != null) return cached.isEmpty();
    final long s = get();
    if (s == PROMOTED) {
      final Overflow queue = overflow;
      // Null while the winning promoter is between the CAS and the store.
      // The messages are already in that promoter's queue, so this is not empty.
      return queue != null && queue.isEmpty();
    }
    return (s & 1L) == 0L;
  }

  private Overflow promoted() {
    Overflow queue;
    while ((queue = overflow) == null) Thread.onSpinWait();
    return queue;
  }

  /**
   * Vyukov non-intrusive MPSC queue. Producers linearize on {@link
   * #getAndSet}; the consumer walks {@code next} links. {@code producer ==
   * consumer} means empty, including the case where {@code next} is still null
   * because the producer has not linked the node yet.
   */
  static final class Overflow extends AtomicReference<Overflow.Node> {
    static final VarHandle NEXT;

    static {
      try {
        NEXT = MethodHandles.lookup().findVarHandle(Node.class, "next", Node.class);
      } catch (ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    private Node consumerNode;

    private Overflow(final Node producer, final Node consumer) {
      super(producer);
      this.consumerNode = consumer;
    }

    static Overflow build(final FiberMessage first, final FiberMessage second) {
      final Node stub = new Node(null);
      final Node n1 = new Node(first);
      final Node n2 = new Node(second);
      // Link before the Overflow object is published through FiberMailbox.overflow,
      // so the consumer never observes a bubble for these two messages.
      NEXT.setRelease(n1, n2);
      NEXT.setRelease(stub, n1);
      return new Overflow(n2, stub);
    }

    void add(final FiberMessage message) {
      final Node node = new Node(message);
      final Node prev = getAndSet(node);
      NEXT.setRelease(prev, node);
    }

    FiberMessage pollRelaxed() {
      final Node curr = consumerNode;
      final Node next = (Node) NEXT.getAcquire(curr);
      if (next == null) return null;
      return take(curr, next);
    }

    FiberMessage poll() {
      final Node curr = consumerNode;
      Node next = (Node) NEXT.getAcquire(curr);
      if (next == null) {
        if (get() == curr) return null;
        do {
          Thread.onSpinWait();
          next = (Node) NEXT.getAcquire(curr);
        } while (next == null);
      }
      return take(curr, next);
    }

    boolean isEmpty() {
      // producerNode != consumerNode covers a producer that has swapped the
      // tail but has not stored next yet.
      return get() == consumerNode;
    }

    private FiberMessage take(final Node curr, final Node next) {
      final FiberMessage value = next.value;
      next.value = null;
      // Self-link so the detached prefix does not keep the live chain reachable.
      NEXT.setRelease(curr, curr);
      consumerNode = next;
      return value;
    }

    static final class Node {
      FiberMessage value;
      volatile Node next;

      Node(final FiberMessage value) {
        this.value = value;
      }
    }
  }
}
