/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio.stm

import java.util.concurrent.atomic.AtomicReference

import scalaz.zio.UIO
import scalaz.zio.stm.STM.internal._

/**
 * A variable that can be modified as part of a transactional effect.
 */
class TRef[A] private (
  private[stm] val id: Long,
  @volatile private[stm] var versioned: Versioned[A],
  private[stm] val todo: AtomicReference[Map[Long, Todo]]
) {
  self =>

  /**
   * Retrieves the value of the `TRef`.
   */
  final val get: STM[Nothing, A] =
    new STM(journal => {
      val entry = getOrMakeEntry(journal)

      TRez.Succeed(entry.unsafeGet[A])
    })

  /**
   * Sets the value of the `TRef`.
   */
  final def set(newValue: A): STM[Nothing, Unit] =
    new STM(journal => {
      val entry = getOrMakeEntry(journal)

      entry unsafeSet newValue

      succeedUnit
    })

  override final def toString =
    s"TRef(id = $id, versioned.value = ${versioned.value}, todo = ${todo.get})"

  /**
   * Updates the value of the variable.
   */
  final def update(f: A => A): STM[Nothing, A] =
    new STM(journal => {
      val entry = getOrMakeEntry(journal)

      val newValue = f(entry.unsafeGet[A])

      entry unsafeSet newValue

      TRez.Succeed(newValue)
    })

  /**
   * Updates some values of the variable but leaves others alone.
   */
  final def updateSome(f: PartialFunction[A, A]): STM[Nothing, A] =
    update(f orElse { case a => a })

  /**
   * Updates the value of the variable, returning a function of the specified
   * value.
   */
  final def modify[B](f: A => (B, A)): STM[Nothing, B] =
    new STM(journal => {
      val entry = getOrMakeEntry(journal)

      val (retValue, newValue) = f(entry.unsafeGet[A])

      entry unsafeSet newValue

      TRez.Succeed(retValue)
    })

  /**
   * Updates some values of the variable, returning a function of the specified
   * value or the default.
   */
  final def modifySome[B](default: B)(f: PartialFunction[A, (B, A)]): STM[Nothing, B] =
    modify { a =>
      f.lift(a).getOrElse((default, a))
    }

  private final def getOrMakeEntry(journal: Journal): Entry =
    if (journal containsKey id) journal.get(id)
    else {
      val expected = versioned
      val entry    = Entry(self, expected.value, expected)
      journal put (id, entry)
      entry
    }
}

object TRef {

  /**
   * Makes a new `TRef` that is initialized to the specified value.
   */
  final def make[A](a: => A): STM[Nothing, TRef[A]] =
    new STM(journal => {
      val id = makeTRefId()

      val value     = a
      val versioned = new Versioned(value)

      val todo = new AtomicReference[Map[Long, Todo]](Map())

      val tvar = new TRef(id, versioned, todo)

      journal.put(id, Entry(tvar, value, versioned))

      TRez.Succeed(tvar)
    })

  /**
   * A convenience method that makes a `TRef` and immediately commits the
   * transaction to extract the value out.
   */
  final def makeCommit[A](a: => A): UIO[TRef[A]] =
    STM.atomically(make(a))
}
