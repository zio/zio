/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import zio.internal.MutableConcurrentQueue;

/*
 * NOTE: these classes need to be implemented in Java, because:
 *   1) `head` and `tail` need to be naked protected or public fields
 *   in order to be accessible by `AtomicLongFieldUpdater`.
 *   2) there doesn't seems to be a way to expose naked public or
 *   protected fields in Scala is it generates accessor methods for
 *   those.
 *
 * The classes below provide padding for contended fields in the
 * subclasses. The padding is necessary because of the false sharing
 * problem.
 *
 * See: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6549128
 */

@SuppressWarnings("serial")
abstract class MutableQueueFieldsPadding0 {
  // To prevent false sharing, we need to ensure that the head and tail
  // fields are on different cache lines. We do this by padding the
  // fields with dummy fields.
  protected volatile long head;
  protected long p1, p2, p3, p4, p5, p6;
}

@SuppressWarnings("serial")
abstract class MutableQueueFieldsPadding1 extends MutableQueueFieldsPadding0 {
  protected long p7, p8, p9, pa, pb, pc;
  protected volatile long tail;
  protected long pd, pe, pf, pg, ph, pi;
}