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
 * A `ZTRef[EA, EB, A, B]` is a polymorphic, purely functional description of a
 * mutable reference that can be modified as part of a transactional effect. The
 * fundamental operations of a `ZTRef` are `set` and `get`. `set` takes a value
 * of type `A` and transactionally sets the reference to a new value, potentially
 * failing with an error of type `EA`. `get` gets the current value of the reference
 * and returns a value of type `B`, potentially failing with an error of type `EB`.
 *
 * When the error and value types of the `ZTRef` are unified, that is, it is a
 * `ZTRef[E, E, A, A]`, the `ZTRef` also supports atomic `modify` and `update`
 * operations. All operations are guaranteed to be executed transactionally.
 *
 * NOTE: While `ZTRef` provides the transactional equivalent of a mutable reference,
 * the value inside the `ZTRef` should be immutable. For performance reasons `ZTRef`
 * is implemented in terms of compare and swap operations rather than synchronization.
 * These operations are not safe for mutable values that do not support concurrent
 * access.
 */
sealed trait ZTRef[+EA, +EB, -A, +B] extends Serializable { self =>

  def fold[EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ca: C => Either[EC, A],
    bc: B => Either[ED, D]
  ): ZTRef[EC, ED, C, D]

  /**
   * Retrieves the value of the `TRef`.
   */
  val get: STM[EB, B]

  /**
   * Sets the value of the `TRef`.
   */
  def set(a: A): STM[EA, Unit]

  final def collect[C](pf: PartialFunction[B, C]): ZTRef[EA, Option[EB], A, C] =
    fold(identity, Some(_), Right(_), pf.lift(_).fold[Either[Option[EB], C]](Left(None))(Right(_)))

  final def contramap[C](f: C => A): ZTRef[EA, EB, C, B] =
    contramapEither(c => Right(f(c)))

  final def contramapEither[EC >: EA, C](f: C => Either[EC, A]): ZTRef[EC, EB, C, B] =
    dimapEither(f, Right(_))

  final def dimap[C, D](f: C => A, g: B => D): ZTRef[EA, EB, C, D] =
    dimapEither(c => Right(f(c)), b => Right(g(b)))

  final def dimapEither[EC >: EA, ED >: EB, C, D](f: C => Either[EC, A], g: B => Either[ED, D]): ZTRef[EC, ED, C, D] =
    fold(identity, identity, f, g)

  final def dimapError[EC, ED](f: EA => EC, g: EB => ED): ZTRef[EC, ED, A, B] =
    fold(f, g, Right(_), Right(_))

  final def filter(f: B => Boolean): ZTRef[EA, Option[EB], A, B] =
    fold(identity, Some(_), Right(_), b => if (f(b)) Right(b) else Left(None))

  final def map[C](f: B => C): ZTRef[EA, EB, A, C] =
    mapEither(b => Right(f(b)))

  final def mapEither[EC >: EB, C](f: B => Either[EC, C]): ZTRef[EA, EC, A, C] =
    dimapEither(Right(_), f)

  final def readOnly: ZTRef[EA, EB, Nothing, B] =
    self

  final def unifyError[E](ea: EA => E, eb: EB => E): ZTRef[E, E, A, B] =
    dimapError(ea, eb)

  final def unifyValue[C](ca: C => A, bc: B => C): ZTRef[EA, EB, C, C] =
    dimap(ca, bc)

  final def writeOnly: ZTRef[EA, Unit, A, Nothing] =
    fold(identity, _ => (), Right(_), _ => Left(()))
}

object ZTRef {

  private[stm] final class Atomic[A](
    @volatile private[stm] var versioned: Versioned[A],
    private[stm] val todo: AtomicReference[Map[TxnId, Todo]]
  ) extends ZTRef[Nothing, Nothing, A, A] { self =>

    final def fold[EC, ED, C, D](
      ea: Nothing => EC,
      eb: Nothing => ED,
      ca: C => Either[EC, A],
      bc: A => Either[ED, D]
    ): ZTRef[EC, ED, C, D] =
      new Derived[EC, ED, C, D] {
        type S = A
        def getEither(s: S): Either[ED, D] = bc(s)
        def setEither(c: C): Either[EC, S] = ca(c)
        val value: Atomic[S]               = self
      }

    final val get: USTM[A] =
      new ZSTM((journal, _, _, _) => {
        val entry = getOrMakeEntry(journal)
        TExit.Succeed(entry.unsafeGet[A])
      })

    final def set(a: A): USTM[Unit] =
      new ZSTM((journal, _, _, _) => {
        val entry = getOrMakeEntry(journal)
        entry.unsafeSet(a)
        TExit.Succeed(())
      })

    /**
     * Sets the value of the `TRef` and returns the old value.
     */
    def getAndSet(a: A): USTM[A] =
      new ZSTM((journal, _, _, _) => {
        val entry    = getOrMakeEntry(journal)
        val oldValue = entry.unsafeGet[A]
        entry.unsafeSet(a)
        TExit.Succeed(oldValue)
      })

    /**
     * Updates the value of the variable and returns the old value.
     */
    def getAndUpdate(f: A => A): USTM[A] =
      new ZSTM((journal, _, _, _) => {
        val entry    = getOrMakeEntry(journal)
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
        val entry                = getOrMakeEntry(journal)
        val (retValue, newValue) = f(entry.unsafeGet[A])
        entry.unsafeSet(newValue)
        TExit.Succeed(retValue)
      })

    /**
     * Updates the value of the variable, returning a function of the specified
     * value.
     */
    def modifySome[B](default: B)(f: PartialFunction[A, (B, A)]): USTM[B] =
      modify(a => f.lift(a).getOrElse((default, a)))

    override def toString =
      s"ZTRef.Atomic(id = ${self.hashCode()}, versioned.value = ${versioned.value}, todo = ${todo.get})"

    /**
     * Updates the value of the variable.
     */
    def update(f: A => A): USTM[Unit] =
      new ZSTM((journal, _, _, _) => {
        val entry    = getOrMakeEntry(journal)
        val newValue = f(entry.unsafeGet[A])
        entry.unsafeSet(newValue)
        TExit.Succeed(())
      })

    /**
     * Updates the value of the variable and returns the new value.
     */
    def updateAndGet(f: A => A): USTM[A] =
      new ZSTM((journal, _, _, _) => {
        val entry    = getOrMakeEntry(journal)
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

  private abstract class Derived[+EA, +EB, -A, +B] extends ZTRef[EA, EB, A, B] { self =>
    type S

    def getEither(s: S): Either[EB, B]

    def setEither(a: A): Either[EA, S]

    val value: Atomic[S]

    final def fold[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ca: C => Either[EC, A],
      bc: B => Either[ED, D]
    ): ZTRef[EC, ED, C, D] =
      new Derived[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bc)
        def setEither(c: C): Either[EC, S] =
          ca(c).flatMap(a => self.setEither(a).fold(e => Left(ea(e)), Right(_)))
        val value: Atomic[S] =
          self.value
      }

    final val get: STM[EB, B] =
      value.get.flatMap(getEither(_).fold(STM.fail(_), STM.succeedNow))

    final def set(a: A): STM[EA, Unit] =
      setEither(a).fold(STM.fail(_), value.set)
  }

  implicit class UnifiedSyntax[E, A](private val self: ETRef[E, A]) extends AnyVal {
    final def getAndSet(a: A): STM[E, A] =
      self match {
        case atomic: Atomic[A]            => atomic.getAndSet(a)
        case derived: Derived[E, E, A, A] => derived.modify((_, a))
      }

    final def getAndUpdate(f: A => A): STM[E, A] =
      self match {
        case atomic: Atomic[A]            => atomic.getAndUpdate(f)
        case derived: Derived[E, E, A, A] => derived.modify(v => (v, f(v)))
      }

    final def getAndUpdateSome(pf: PartialFunction[A, A]): STM[E, A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndUpdateSome(pf)
        case derived: Derived[E, E, A, A] =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            (v, result)
          }
      }

    final def modify[B](f: A => (B, A)): STM[E, B] =
      self match {
        case atomic: Atomic[A] => atomic.modify(f)
        case derived: Derived[E, E, A, A] =>
          derived.value.modify { s =>
            derived.getEither(s) match {
              case Left(e) => (Left(e), s)
              case Right(a1) =>
                val (b, a2) = f(a1)
                derived.setEither(a2) match {
                  case Left(e)  => (Left(e), s)
                  case Right(s) => (Right(b), s)
                }
            }
          }.absolve
      }

    final def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): STM[E, B] =
      self match {
        case atomic: Atomic[A] => atomic.modifySome(default)(pf)
        case derived: Derived[E, E, A, A] =>
          derived.modify(v => pf.applyOrElse[A, (B, A)](v, _ => (default, v)))
      }

    def update(f: A => A): STM[E, Unit] =
      self match {
        case atomic: Atomic[A]            => atomic.update(f)
        case derived: Derived[E, E, A, A] => derived.modify(v => ((), f(v)))
      }

    def updateAndGet(f: A => A): STM[E, A] =
      self match {
        case atomic: Atomic[A] => atomic.updateAndGet(f)
        case derived: Derived[E, E, A, A] =>
          derived.modify { v =>
            val result = f(v)
            (result, result)
          }
      }

    def updateSome(pf: PartialFunction[A, A]): STM[E, Unit] =
      self match {
        case atomic: Atomic[A] => atomic.updateSome(pf)
        case derived: Derived[E, E, A, A] =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            ((), result)
          }
      }

    def updateSomeAndGet(pf: PartialFunction[A, A]): STM[E, A] =
      self match {
        case atomic: Atomic[A] => atomic.updateSomeAndGet(pf)
        case derived: Derived[E, E, A, A] =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            (result, result)
          }
      }
  }

  /**
   * Makes a new `TRef` that is initialized to the specified value.
   */
  def make[A](a: => A): USTM[TRef[A]] =
    new ZSTM((journal, _, _, _) => {
      val value     = a
      val versioned = new Versioned(value)
      val todo      = new AtomicReference[Map[TxnId, Todo]](Map())
      val tref      = new Atomic(versioned, todo)
      journal.put(tref, Entry(tref, true))
      TExit.Succeed(tref)
    })

  /**
   * A convenience method that makes a `TRef` and immediately commits the
   * transaction to extract the value out.
   */
  def makeCommit[A](a: => A): UIO[TRef[A]] =
    STM.atomically(make(a))

  private[stm] def unsafeMake[A](a: A): TRef[A] = {
    val value     = a
    val versioned = new Versioned(value)
    val todo      = new AtomicReference[Map[TxnId, Todo]](Map())
    new Atomic(versioned, todo)
  }
}
