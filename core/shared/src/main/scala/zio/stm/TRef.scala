/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import com.github.ghik.silencer.silent
import zio.{UIO, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.ZSTM.internal._

import java.util.concurrent.atomic.AtomicReference

/**
 * A `ZTRef[EA, EB, A, B]` is a polymorphic, purely functional description of a
 * mutable reference that can be modified as part of a transactional effect. The
 * fundamental operations of a `ZTRef` are `set` and `get`. `set` takes a value
 * of type `A` and transactionally sets the reference to a new value,
 * potentially failing with an error of type `EA`. `get` gets the current value
 * of the reference and returns a value of type `B`, potentially failing with an
 * error of type `EB`.
 *
 * When the error and value types of the `ZTRef` are unified, that is, it is a
 * `ZTRef[E, E, A, A]`, the `ZTRef` also supports atomic `modify` and `update`
 * operations. All operations are guaranteed to be executed transactionally.
 *
 * NOTE: While `ZTRef` provides the transactional equivalent of a mutable
 * reference, the value inside the `ZTRef` should be immutable. For performance
 * reasons `ZTRef` is implemented in terms of compare and swap operations rather
 * than synchronization. These operations are not safe for mutable values that
 * do not support concurrent access.
 */
sealed abstract class TRef[A] extends Serializable { self =>

  protected def atomic: TRef.Atomic[_]

  /**
   * Retrieves the value of the `ZTRef`.
   */
  def get: STM[Nothing, A]

  /**
   * Sets the value of the `ZTRef`.
   */
  def set(a: A): STM[Nothing, Unit]

  private[stm] def unsafeGet(journal: Journal): A =
    atomic.getOrMakeEntry(journal).unsafeGet

  private[stm] def unsafeSet(journal: Journal, a: A): Unit =
    atomic.getOrMakeEntry(journal).unsafeSet(a)
}

object TRef {

  private[stm] final class Atomic[A](
    @volatile private[stm] var versioned: Versioned[A],
    private[stm] val todo: AtomicReference[Map[TxnId, Todo]]
  ) extends TRef[A] { self =>

    def get: USTM[A] =
      ZSTM.Effect((journal, _, _) => getOrMakeEntry(journal).unsafeGet[A])

    def set(a: A): USTM[Unit] =
      ZSTM.Effect { (journal, _, _) =>
        val entry = getOrMakeEntry(journal)
        entry.unsafeSet(a)
        ()
      }

    /**
     * Sets the value of the `ZTRef` and returns the old value.
     */
    def getAndSet(a: A): USTM[A] =
      ZSTM.Effect { (journal, _, _) =>
        val entry    = getOrMakeEntry(journal)
        val oldValue = entry.unsafeGet[A]
        entry.unsafeSet(a)
        oldValue
      }

    /**
     * Updates the value of the variable and returns the old value.
     */
    def getAndUpdate(f: A => A): USTM[A] =
      ZSTM.Effect { (journal, _, _) =>
        val entry    = getOrMakeEntry(journal)
        val oldValue = entry.unsafeGet[A]
        entry.unsafeSet(f(oldValue))
        oldValue
      }

    /**
     * Updates some values of the variable but leaves others alone, returning
     * the old value.
     */
    def getAndUpdateSome(f: PartialFunction[A, A]): USTM[A] =
      getAndUpdate(f orElse { case a => a })

    /**
     * Updates the value of the variable, returning a function of the specified
     * value.
     */
    def modify[B](f: A => (B, A)): USTM[B] =
      ZSTM.Effect { (journal, _, _) =>
        val entry                = getOrMakeEntry(journal)
        val (retValue, newValue) = f(entry.unsafeGet[A])
        entry.unsafeSet(newValue)
        retValue
      }

    /**
     * Updates the value of the variable, returning a function of the specified
     * value.
     */
    def modifySome[B](default: B)(f: PartialFunction[A, (B, A)]): USTM[B] =
      modify(a => f.lift(a).getOrElse((default, a)))

    override def toString: String =
      s"ZTRef.Atomic(id = ${self.hashCode()}, versioned.value = ${versioned.value}, todo = ${todo.get})"

    /**
     * Updates the value of the variable.
     */
    def update(f: A => A): USTM[Unit] =
      ZSTM.Effect { (journal, _, _) =>
        val entry    = getOrMakeEntry(journal)
        val newValue = f(entry.unsafeGet[A])
        entry.unsafeSet(newValue)
        ()
      }

    /**
     * Updates the value of the variable and returns the new value.
     */
    def updateAndGet(f: A => A): USTM[A] =
      ZSTM.Effect { (journal, _, _) =>
        val entry    = getOrMakeEntry(journal)
        val newValue = f(entry.unsafeGet[A])
        entry.unsafeSet(newValue)
        newValue
      }

    /**
     * Updates some values of the variable but leaves others alone.
     */
    def updateSome(f: PartialFunction[A, A]): USTM[Unit] =
      update(f orElse { case a => a })

    /**
     * Updates some values of the variable but leaves others alone, returning
     * the new value.
     */
    def updateSomeAndGet(f: PartialFunction[A, A]): USTM[A] =
      updateAndGet(f orElse { case a => a })

    protected val atomic: Atomic[_] = self

    private[stm] def getOrMakeEntry(journal: Journal): Entry =
      if (journal.containsKey(self)) journal.get(self)
      else {
        val entry = Entry(self, false)
        journal.put(self, entry)
        entry
      }
  }

  implicit class UnifiedSyntax[A](private val self: TRef[A]) extends AnyVal {
    def getAndSet(a: A): STM[Nothing, A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndSet(a)
        case derived           => derived.modify((_, a))
      }

    def getAndUpdate(f: A => A): STM[Nothing, A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndUpdate(f)
        case derived           => derived.modify(v => (v, f(v)))
      }

    def getAndUpdateSome(pf: PartialFunction[A, A]): STM[Nothing, A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndUpdateSome(pf)
        case derived =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            (v, result)
          }
      }

    @silent("unreachable code")
    def modify[B](f: A => (B, A)): STM[Nothing, B] =
      self match {
        case atomic: Atomic[A] => atomic.modify(f)
      }

    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): STM[Nothing, B] =
      self match {
        case atomic: Atomic[A] => atomic.modifySome(default)(pf)
      }

    def update(f: A => A): STM[Nothing, Unit] =
      self match {
        case atomic: Atomic[A] => atomic.update(f)
      }

    def updateAndGet(f: A => A): STM[Nothing, A] =
      self match {
        case atomic: Atomic[A] => atomic.updateAndGet(f)
      }

    def updateSome(pf: PartialFunction[A, A]): STM[Nothing, Unit] =
      self match {
        case atomic: Atomic[A] => atomic.updateSome(pf)
      }

    def updateSomeAndGet(pf: PartialFunction[A, A]): STM[Nothing, A] =
      self match {
        case atomic: Atomic[A] => atomic.updateSomeAndGet(pf)
      }
  }

  /**
   * Makes a new `ZTRef` that is initialized to the specified value.
   */
  def make[A](a: => A): USTM[TRef[A]] =
    ZSTM.Effect { (journal, _, _) =>
      val value     = a
      val versioned = new Versioned(value)
      val todo      = new AtomicReference[Map[TxnId, Todo]](Map())
      val tref      = new Atomic(versioned, todo)
      journal.put(tref, Entry(tref, true))
      tref
    }

  /**
   * A convenience method that makes a `ZTRef` and immediately commits the
   * transaction to extract the value out.
   */
  def makeCommit[A](a: => A)(implicit trace: ZTraceElement): UIO[TRef[A]] =
    STM.atomically(make(a))

  private[stm] def unsafeMake[A](a: A): TRef[A] = {
    val value     = a
    val versioned = new Versioned(value)
    val todo      = new AtomicReference[Map[TxnId, Todo]](Map())
    new Atomic(versioned, todo)
  }
}
