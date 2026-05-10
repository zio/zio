/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class FiberMailbox {
  private static final AtomicReferenceFieldUpdater<FiberMailbox, Node> PRODUCER_NODE =
      AtomicReferenceFieldUpdater.newUpdater(FiberMailbox.class, Node.class, "producerNode");

  private Node consumerNode;
  @SuppressWarnings("unused")
  private volatile Node producerNode;

  FiberMailbox() {
    final Node node = new Node(null);
    consumerNode = node;
    producerNode = node;
  }

  void add(final FiberMessage message) {
    Objects.requireNonNull(message);

    final Node node = new Node(message);
    final Node previous = PRODUCER_NODE.getAndSet(this, node);
    // Runtime rescheduling observes this link with one-shot post-drain checks.
    previous.set(node);
  }

  FiberMessage poll() {
    final Node consumer = consumerNode;
    final Node next = consumer.get();

    if (next == null) {
      return null;
    }

    final FiberMessage message = next.message;
    next.message = null;
    consumer.lazySet(consumer);
    consumerNode = next;
    return message;
  }

  boolean hasLinkedMessages() {
    // Fast path for rescheduling checks. This only reports messages already
    // linked from the consumer node. A cross-thread producer that has published
    // the tail but not linked it yet will schedule the fiber after add returns.
    // Run-loop-local producers complete add before yielding.
    return consumerNode.get() != null;
  }

  boolean isDefinitelyEmpty() {
    // Used before completing a fiber, where an in-flight producer must keep it
    // alive even if the new tail has not been linked from the consumer node yet.
    return consumerNode == producerNode;
  }

  private static final class Node extends AtomicReference<Node> {
    private static final long serialVersionUID = 0L;

    private FiberMessage message;

    private Node(final FiberMessage message) {
      this.message = message;
    }
  }
}
