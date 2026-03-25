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

package zio.internal

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * A specialized MPSC (multi-producer, single-consumer) mailbox for fiber
 * messages.
 *
 * ==State machine==
 *
 * A single [[AtomicReference]] advances through three states:
 *   - `null` — empty
 *   - [[FiberMessage]] — exactly one message (zero-allocation fast path)
 *   - `ConcurrentLinkedQueue[_]` — two or more messages (contention path)
 *
 * {{{
 *   null  ──add──▶  FiberMessage  ──add──▶  CLQ
 *    ▲                 │                     │
 *    └──── poll ───────┘          (retained; reused on future bursts)
 * }}}
 *
 * The single-slot fast path eliminates all heap allocation for the
 * overwhelmingly common case of one outstanding message (e.g.
 * resume-after-async). Under contention a `ConcurrentLinkedQueue` is promoted
 * once and retained for the lifetime of the fiber, so future bursts reuse the
 * same CLQ object — no re-allocation per burst cycle.
 *
 * All coordination uses CAS operations only; no locks are held anywhere.
 *
 * ==Why not a fixed 4-slot array?==
 *
 * Issue #8807 proposed a ring-buffer of four pre-allocated `AtomicReference`
 * slots plus an overflow `CLQ` for bursts beyond four. That design requires
 * each writer to (1) atomically claim a write index and (2) separately store
 * its message into the claimed slot, while the single reader advances an
 * independent read index.
 *
 * This two-step write introduces a FIFO-ordering hazard at the ring-to-overflow
 * boundary. Consider the following interleaving:
 *
 *   1. The ring fills to capacity (4 messages in slots). 2. Writer A finds the
 *      ring full and calls `overflow.add(msgA)`. 3. The reader drains one slot,
 *      making room. 4. Writer B finds the ring non-full AND overflow non-empty,
 *      but — because the "check overflow.isEmpty" and the "CAS the write index"
 *      are not a single atomic operation — Writer B successfully claims a slot
 *      and stores `msgB` there. 5. The reader drains `msgB` from the slot
 *      before it drains `msgA` from overflow, even though `msgA` was enqueued
 *      first.
 *
 * Preventing this requires an additional coordination primitive (e.g. a mode
 * flag that is set atomically with the overflow enqueue) — which reintroduces
 * exactly the contention overhead the slot array was meant to avoid.
 *
 * The three-state machine sidesteps this entirely: all messages flow through a
 * single, inherently-FIFO structure (the CLQ), so ordering is guaranteed
 * without extra synchronisation.
 *
 * ==Performance==
 *
 * | Pattern                 | Allocation      | CAS ops |
 * |:------------------------|:----------------|:--------|
 * | Single add + poll       | zero            | 2       |
 * | Burst of N (first time) | 1 CLQ + N nodes | N+1     |
 * | Burst of N (subsequent) | N CLQ nodes     | 0       |
 * | isEmpty (empty)         | zero            | 0       |
 *
 * ==Memory layout==
 *
 * `FiberRuntime` mixes in `FiberMailbox` directly, so `state` lives inside the
 * `FiberRuntime` object — one fewer pointer indirection per mailbox access and
 * better cache co-location with the other hot runtime fields.
 *
 * Seven `Long` padding fields flank `state` on each side so that it occupies
 * its own 64-byte cache line and cannot be falsely shared with adjacent
 * `FiberRuntime` fields on multi-core machines.
 *
 * ==Single-consumer guarantee==
 *
 * [[poll]] is only safe when called from a single consumer thread at a time.
 * `FiberRuntime` enforces this via its `running: AtomicBoolean`:
 *
 *   - Before any call to `poll()`, the runtime wins a
 *     `running.compareAndSet(false, true)` CAS, which atomically "claims" the
 *     consumer role.
 *   - While `running` is `true`, no other thread can claim the consumer role
 *     (their CAS will fail), so `poll()` is always called by exactly one thread
 *     at a time.
 *   - `running` is set back to `false` only after the drain loop exits, at
 *     which point the runtime re-checks [[isEmpty]] before deciding to park
 *     (see Park/unpark safety below).
 *
 * Consequence: even though `FiberMailbox` itself does not prevent concurrent
 * calls to `poll()`, the surrounding `running` gate in `FiberRuntime` provides
 * the invariant at a higher level, making the single-consumer property a
 * system-level guarantee rather than a per-method contract.
 *
 * ==Park / unpark safety==
 *
 * `FiberRuntime.drainQueueOnCurrentThread` performs a volatile write on
 * `running` (setting it to `false`) and then re-checks [[isEmpty]] before
 * deciding to park. Because the volatile write on `running` happens-before the
 * volatile read inside `state.get()` in [[isEmpty]], any [[add]] that completes
 * after the drain loop but before the isEmpty check is guaranteed to be visible
 * — making it impossible to miss a message.
 */
private[zio] trait FiberMailbox {

  // Cache-line padding: prevents false sharing between `state` and adjacent
  // FiberRuntime fields when multiple fibers' runtimes land on the same cache
  // line.  Each padding var is 8 bytes; 7 vars + the AtomicReference header
  // (16 bytes on a compressed-oops JVM) spans a full 64-byte cache line.
  private[this] var _p0, _p1, _p2, _p3, _p4, _p5, _p6 = 0L

  // Holds: null | FiberMessage | ConcurrentLinkedQueue[FiberMessage]
  private[this] val state = new AtomicReference[AnyRef](null)

  private[this] var _q0, _q1, _q2, _q3, _q4, _q5, _q6 = 0L

  /**
   * Enqueue a message. Safe to call from any thread concurrently.
   *
   * Fast path (state == null): one CAS, zero allocation. Slow path (state ==
   * FiberMessage): allocates one CLQ and promotes. CLQ path (state == CLQ):
   * appends to the existing CLQ; no allocation beyond the CLQ node that
   * `ConcurrentLinkedQueue.add` itself creates.
   */
  @tailrec
  final def add(msg: FiberMessage): Unit = {
    val current = state.get()
    if (current eq null) {
      if (!state.compareAndSet(null, msg)) add(msg)
    } else
      current match {
        case q: ConcurrentLinkedQueue[FiberMessage] @unchecked =>
          q.add(msg)
        case existing: FiberMessage =>
          val q = new ConcurrentLinkedQueue[FiberMessage]()
          q.add(existing)
          q.add(msg)
          if (!state.compareAndSet(existing, q)) add(msg)
        case _ => add(msg) // unreachable; guards against MatchError
      }
  }

  /**
   * Dequeue and return the next message, or `null` if the mailbox is empty.
   * Must be called from a single consumer thread only.
   *
   * When state is a single [[FiberMessage]] the CAS resets it to `null`,
   * recovering the zero-allocation fast path for the next message. When state
   * is a CLQ the CLQ is polled directly; the CLQ object is retained so that
   * future bursts reuse it without re-allocation.
   */
  @tailrec
  final def poll(): FiberMessage = {
    val current = state.get()
    if (current eq null) null
    else
      current match {
        case q: ConcurrentLinkedQueue[FiberMessage] @unchecked =>
          q.poll()
        case msg: FiberMessage =>
          if (state.compareAndSet(msg, null)) msg else poll()
        case _ => poll() // unreachable; guards against MatchError
      }
  }

  /**
   * Returns `true` if the mailbox contains no pending messages.
   *
   * This is an *approximate* check: a concurrent [[add]] may make the result
   * immediately stale. The fiber run loop compensates with the volatile fence
   * in `running.set(false)` / `running.compareAndSet(false, true)`, which
   * ensures that any racing [[add]] is always visible before the fiber decides
   * to park (see `drainQueueOnCurrentThread`).
   */
  final def isEmpty: Boolean = {
    val current = state.get()
    if (current eq null) true
    else
      current match {
        case q: ConcurrentLinkedQueue[FiberMessage] @unchecked => q.isEmpty
        case _                                                 => false
      }
  }
}
