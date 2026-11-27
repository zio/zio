/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio.stm

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM.internal._

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

/**
 * A `TRef` is a mutable reference that is read and changed inside STM
 * transactions. `get` reads the value, and `set`, `update` and `modify` change
 * it. Other fibers see the changes when the transaction commits; a transaction
 * that fails, dies or retries leaves the `TRef` unchanged.
 *
 * {{{
 * import zio._
 *
 * val balances: UIO[(Int, Int)] =
 *   for {
 *     from <- TRef.makeCommit(100)
 *     to   <- TRef.makeCommit(0)
 *     _    <- (from.update(_ - 30) *> to.update(_ + 30)).commit
 *     both <- from.get.zip(to.get).commit
 *   } yield both
 * // (70,30)
 * }}}
 *
 * The value inside a `TRef` should be immutable. A transaction that fails, dies
 * or retries restores the old value of the `TRef`, but it does not undo
 * in-place changes to a mutable object (an `ArrayBuffer`, for example) held in
 * it, and a transaction that runs again makes those changes again.
 *
 * @see
 *   [[https://zio.dev/reference/stm/tref/ TRef]]
 */
final class TRef[A] private (
  private[stm] val versioned: AtomicReference[A],
  @volatile private[stm] var todo: Map[TxnId, Todo] = Map.empty
) extends Serializable { self =>

  /**
   * Builds a `TRef` that holds `versioned.value`, with the callbacks of the
   * suspended transactions in `todo`. Kept for binary compatibility: its
   * parameter types are private to the `zio.stm` package, so code outside that
   * package can pass it only `null`, which throws a
   * `java.lang.NullPointerException`. Use `TRef.make` instead.
   */
  @deprecated("kept for binary compatibility only", "2.1.8")
  def this(versioned: Versioned[A], todo: AtomicReference[Map[TxnId, Todo]]) =
    this(new AtomicReference(versioned.value), todo.get())

  private[stm] val lock: ZSTMLockSupport.Lock = ZSTMLockSupport.Lock(true)

  private val id = TRef.idCounter.incrementAndGet()

  private[this] val hc = super.hashCode()

  override def hashCode(): Int = hc

  /**
   * Reads the value of the `TRef`, including changes made earlier in the same
   * transaction.
   */
  def get: USTM[A] =
    ZSTM.Effect((journal, _, _) => getOrMakeEntry(journal).unsafeGet[A])

  /**
   * Sets the value of the `TRef` to `a` and returns the old value.
   */
  def getAndSet(a: A): USTM[A] =
    ZSTM.Effect { (journal, _, _) =>
      val entry    = getOrMakeEntry(journal)
      val oldValue = entry.unsafeGet[A]
      entry.unsafeSet(a)
      oldValue
    }

  /**
   * Replaces the value of the `TRef` with `f` applied to it and returns the old
   * value.
   */
  def getAndUpdate(f: A => A): USTM[A] =
    ZSTM.Effect { (journal, _, _) =>
      val entry    = getOrMakeEntry(journal)
      val oldValue = entry.unsafeGet[A]
      entry.unsafeSet(f(oldValue))
      oldValue
    }

  /**
   * Replaces the value of the `TRef` with `f` applied to it when `f` is defined
   * at the value, and leaves it unchanged otherwise. Returns the old value.
   */
  def getAndUpdateSome(f: PartialFunction[A, A]): USTM[A] =
    getAndUpdate(f orElse { case a => a })

  /**
   * Applies `f` to the value of the `TRef`, stores the second element of the
   * result as the new value and returns the first element.
   */
  def modify[B](f: A => (B, A)): USTM[B] =
    ZSTM.Effect { (journal, _, _) =>
      val entry                = getOrMakeEntry(journal)
      val (retValue, newValue) = f(entry.unsafeGet[A])
      entry.unsafeSet(newValue)
      retValue
    }

  /**
   * Works like `modify` when `f` is defined at the value of the `TRef`. When it
   * is not, leaves the value unchanged and returns `default`.
   */
  def modifySome[B](default: B)(f: PartialFunction[A, (B, A)]): USTM[B] =
    modify(a => f.lift(a).getOrElse((default, a)))

  /**
   * Sets the value of the `TRef` to `a`.
   */
  def set(a: A): USTM[Unit] =
    ZSTM.Effect { (journal, _, _) =>
      val entry = getOrMakeEntry(journal)
      entry.unsafeSet(a)
      ()
    }

  override def toString: String =
    s"TRef(id = ${self.hashCode()}, versioned.value = ${versioned})"

  /**
   * Replaces the value of the `TRef` with `f` applied to it.
   */
  def update(f: A => A): USTM[Unit] =
    ZSTM.Effect { (journal, _, _) =>
      val entry = journal.getOrElseUpdate(self, newEntry())
      entry.unsafeUpdate(f.asInstanceOf[Any => Any])
      ()
    }

  /**
   * Replaces the value of the `TRef` with `f` applied to it and returns the new
   * value.
   */
  def updateAndGet(f: A => A): USTM[A] =
    ZSTM.Effect { (journal, _, _) =>
      val entry    = getOrMakeEntry(journal)
      val newValue = f(entry.unsafeGet[A])
      entry.unsafeSet(newValue)
      newValue
    }

  /**
   * Replaces the value of the `TRef` with `f` applied to it when `f` is defined
   * at the value, and leaves it unchanged otherwise.
   */
  def updateSome(f: PartialFunction[A, A]): USTM[Unit] =
    update(f orElse { case a => a })

  /**
   * Replaces the value of the `TRef` with `f` applied to it when `f` is defined
   * at the value, and leaves it unchanged otherwise. Returns the value the
   * `TRef` holds afterwards.
   */
  def updateSomeAndGet(f: PartialFunction[A, A]): USTM[A] =
    updateAndGet(f orElse { case a => a })

  private[stm] def getOrMakeEntry(journal: Journal): Entry =
    journal.getOrElseUpdate(self, newEntry())

  private[stm] def unsafeGet(journal: Journal): A =
    getOrMakeEntry(journal).unsafeGet

  private[stm] def unsafeSet(journal: Journal, a: A): Unit =
    getOrMakeEntry(journal).unsafeSet(a)

  private[this] val newEntry = () => Entry(self.asInstanceOf[TRef[A & AnyRef]])
}

/**
 * Constructors for [[TRef]], and the `Ordering` the STM runtime uses to sort
 * the `TRef`s of a transaction.
 */
object TRef {
  private val idCounter: AtomicLong = new AtomicLong(0L)

  /**
   * Orders `TRef`s by an identifier that each `TRef` takes from a global
   * counter when it is created, so a `TRef` created earlier comes first. On the
   * JVM and Scala Native, the STM runtime locks the `TRef`s of a transaction in
   * this order when it commits.
   */
  implicit val ordering: Ordering[TRef[?]] = new Ordering[TRef[?]] {
    def compare(x: TRef[?], y: TRef[?]): Int = java.lang.Long.compare(x.id, y.id)
  }

  /**
   * Makes a new `TRef` that is initialized to the specified value. The value is
   * evaluated each time the transaction runs.
   */
  def make[A](a: => A): USTM[TRef[A]] =
    ZSTM.succeed(unsafe.make(a)(Unsafe.unsafe))

  /**
   * Makes a new `TRef` initialized to `a` in a transaction of its own and
   * commits it, returning the `TRef`. The same as `TRef.make(a).commit`.
   */
  def makeCommit[A](a: => A)(implicit trace: Trace): UIO[TRef[A]] =
    STM.atomically(make(a))

  /**
   * Constructors that make a `TRef` outside a transaction. They need an
   * `Unsafe` capability.
   */
  object unsafe {

    /**
     * Makes a new `TRef` initialized to `a` without running a transaction.
     */
    def make[A](a: A)(implicit unsafe: Unsafe): TRef[A] =
      TRef.unsafeMake(a)
  }

  private[stm] def unsafeMake[A](a: A): TRef[A] =
    new TRef(new AtomicReference[A](a))
}
