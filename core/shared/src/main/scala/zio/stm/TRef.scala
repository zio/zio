/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import java.util.concurrent.atomic.AtomicReference

import zio.UIO
import zio.stm.ZSTM.internal._

/**
 * A variable that can be modified as part of a transactional effect.
 */
final class TRef[A] private (
  @volatile private[stm] var versioned: Versioned[A],
  private[stm] val todo: AtomicReference[Map[TxnId, Todo]]
) {
  self =>

  /**
   * Retrieves the value of the `TRef`.
   */
  val get: USTM[A] =
    new ZSTM((journal, _, _, _) => {
      val entry = getOrMakeEntry(journal)

      TExit.Succeed(entry.unsafeGet[A])
    })

  /**
   * Sets the value of the `TRef` and returns the old value.
   */
  def getAndSet(a: A): USTM[A] =
    new ZSTM((journal, _, _, _) => {
      val entry = getOrMakeEntry(journal)

      val oldValue = entry.unsafeGet[A]

      entry.unsafeSet(a)

      TExit.Succeed(oldValue)
    })

  /**
   * Updates the value of the variable and returns the old value.
   */
  def getAndUpdate(f: A => A): USTM[A] =
    new ZSTM((journal, _, _, _) => {
      val entry = getOrMakeEntry(journal)

      val oldValue = entry.unsafeGet[A]

      entry.unsafeSet(f(oldValue))

      TExit.Succeed(oldValue)
    })

  /**
   * Updates some values of the variable but leaves others alone, returning the
   * old value.
   */
  def getAndUpdateSome(f: PartialFunction[A, A]): USTM[A] =
    getAndUpdate(f orElse { case a => a })

  /**
   * Updates the value of the variable, returning a function of the specified
   * value.
   */
  def modify[B](f: A => (B, A)): USTM[B] =
    new ZSTM((journal, _, _, _) => {
      val entry = getOrMakeEntry(journal)

      val (retValue, newValue) = f(entry.unsafeGet[A])

      entry.unsafeSet(newValue)

      TExit.Succeed(retValue)
    })

  /**
   * Updates some values of the variable, returning a function of the specified
   * value or the default.
   */
  def modifySome[B](default: B)(f: PartialFunction[A, (B, A)]): USTM[B] =
    modify(a => f.lift(a).getOrElse((default, a)))

  /**
   * Sets the value of the `TRef`.
   */
  def set(newValue: A): USTM[Unit] =
    new ZSTM((journal, _, _, _) => {
      val entry = getOrMakeEntry(journal)

      entry.unsafeSet(newValue)

      TExit.Succeed(())
    })

  override def toString =
    s"TRef(id = ${self.hashCode()}, versioned.value = ${versioned.value}, todo = ${todo.get})"

  /**
   * Updates the value of the variable.
   */
  def update(f: A => A): USTM[Unit] =
    new ZSTM((journal, _, _, _) => {
      val entry = getOrMakeEntry(journal)

      val newValue = f(entry.unsafeGet[A])

      entry.unsafeSet(newValue)

      TExit.Succeed(())
    })

  /**
   * Updates the value of the variable and returns the new value.
   */
  def updateAndGet(f: A => A): USTM[A] =
    new ZSTM((journal, _, _, _) => {
      val entry = getOrMakeEntry(journal)

      val newValue = f(entry.unsafeGet[A])

      entry.unsafeSet(newValue)

      TExit.Succeed(newValue)
    })

  /**
   * Updates some values of the variable but leaves others alone.
   */
  def updateSome(f: PartialFunction[A, A]): USTM[Unit] =
    update(f orElse { case a => a })

  /**
   * Updates some values of the variable but leaves others alone, returning the
   * new value.
   */
  def updateSomeAndGet(f: PartialFunction[A, A]): USTM[A] =
    updateAndGet(f orElse { case a => a })

  private def getOrMakeEntry(journal: Journal): Entry =
    if (journal.containsKey(self)) journal.get(self)
    else {
      val entry = Entry(self, false)
      journal.put(self, entry)
      entry
    }
}

object TRef {

  /**
   * Makes a new `TRef` that is initialized to the specified value.
   */
  def make[A](a: => A): USTM[TRef[A]] =
    new ZSTM((journal, _, _, _) => {
      val value     = a
      val versioned = new Versioned(value)

      val todo = new AtomicReference[Map[TxnId, Todo]](Map())

      val tref = new TRef(versioned, todo)

      journal.put(tref, Entry(tref, true))

      TExit.Succeed(tref)
    })

  /**
   * A convenience method that makes a `TRef` and immediately commits the
   * transaction to extract the value out.
   */
  def makeCommit[A](a: => A): UIO[TRef[A]] =
    STM.atomically(TRef.make(a))

  private[stm] def unsafeMake[A](a: A): TRef[A] = {
    val value     = a
    val versioned = new Versioned(value)

    val todo = new AtomicReference[Map[TxnId, Todo]](Map())

    new TRef(versioned, todo)
  }
}
