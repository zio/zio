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

package zio

import java.util.concurrent.atomic.AtomicReference

/**
 * A `ZRef[EA, EB, A, B]` is a polymorphic, purely functional description of a
 * mutable reference. The fundamental operations of a `ZRef` are `set` and
 * `get`. `set` takes a value of type `A` and sets the reference to a new
 * value, potentially failing with an error of type `EA`. `get` gets the
 * current value of the reference and returns a value of type `B`, potentially
 * failing with an error of type `EB`.
 *
 * When the error and value types of the `ZRef` are unified, that is, it is a
 * `ZRef[E, E, A, A]`, the `ZRef` also supports atomic `modify` and `update`
 * operations. All operations are guaranteed to be safe for concurrent access.
 *
 * NOTE: While `ZRef` provides the functional equivalent of a mutable
 * reference, the value inside the `ZRef` should be immutable. For performance
 * reasons `ZRef` is implemented in terms of compare and swap operations rather
 * than synchronization. These operations are not safe for mutable values that
 * do not support concurrent access.
 */
sealed trait ZRef[+EA, +EB, -A, +B] extends Serializable { self =>

  /**
   * Folds over the error and value types of the `ZRef`. This is a highly
   * polymorphic method that is capable of arbitrarily transforming the error
   * and value types of the `ZRef`. For most use cases one of the more specific
   * combinators implemented in terms of `fold` will be more ergonomic but this
   * method is extremely useful for implementing new combinators.
   */
  def fold[EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ca: C => Either[EC, A],
    bd: B => Either[ED, D]
  ): ZRef[EC, ED, C, D]

  /**
   * Folds over the error and value types of the `ZRef`, allowing access to
   * the state in transforming the `set` value. This is a more powerful version
   * of `fold` but requires unifying the error types.
   */
  def foldAll[EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ec: EB => EC,
    ca: C => B => Either[EC, A],
    bd: B => Either[ED, D]
  ): ZRef[EC, ED, C, D]

  /**
   * Reads the value from the `ZRef`.
   */
  def get: IO[EB, B]

  /**
   * Writes a new value to the `ZRef`, with a guarantee of immediate
   * consistency (at some cost to performance).
   */
  def set(a: A): IO[EA, Unit]

  /**
   * Writes a new value to the `ZRef` without providing a guarantee of
   * immediate consistency.
   */
  def setAsync(a: A): IO[EA, Unit]

  /**
   * Maps and filters the `get` value of the `ZRef` with the specified partial
   * function, returning a `ZRef` with a `get` value that succeeds with the
   * result of the partial function if it is defined or else fails with `None`.
   */
  final def collect[C](pf: PartialFunction[B, C]): ZRef[EA, Option[EB], A, C] =
    fold(identity, Some(_), Right(_), pf.lift(_).toRight(None))

  /**
   * Transforms the `set` value of the `ZRef` with the specified function.
   */
  final def contramap[C](f: C => A): ZRef[EA, EB, C, B] =
    contramapEither(c => Right(f(c)))

  /**
   * Transforms the `set` value of the `ZRef` with the specified fallible
   * function.
   */
  final def contramapEither[EC >: EA, C](f: C => Either[EC, A]): ZRef[EC, EB, C, B] =
    dimapEither(f, Right(_))

  /**
   * Transforms both the `set` and `get` values of the `ZRef` with the
   * specified functions.
   */
  final def dimap[C, D](f: C => A, g: B => D): ZRef[EA, EB, C, D] =
    dimapEither(c => Right(f(c)), b => Right(g(b)))

  /**
   * Transforms both the `set` and `get` values of the `ZRef` with the
   * specified fallible functions.
   */
  final def dimapEither[EC >: EA, ED >: EB, C, D](f: C => Either[EC, A], g: B => Either[ED, D]): ZRef[EC, ED, C, D] =
    fold(identity, identity, f, g)

  /**
   * Transforms both the `set` and `get` errors of the `ZRef` with the
   * specified functions.
   */
  final def dimapError[EC, ED](f: EA => EC, g: EB => ED): ZRef[EC, ED, A, B] =
    fold(f, g, Right(_), Right(_))

  /**
   * Filters the `set` value of the `ZRef` with the specified predicate,
   * returning a `ZRef` with a `set` value that succeeds if the predicate is
   * satisfied or else fails with `None`.
   */
  final def filterInput[A1 <: A](f: A1 => Boolean): ZRef[Option[EA], EB, A1, B] =
    fold(Some(_), identity, a => if (f(a)) Right(a) else Left(None), Right(_))

  /**
   * Filters the `get` value of the `ZRef` with the specified predicate,
   * returning a `ZRef` with a `get` value that succeeds if the predicate is
   * satisfied or else fails with `None`.
   */
  final def filterOutput(f: B => Boolean): ZRef[EA, Option[EB], A, B] =
    fold(identity, Some(_), Right(_), b => if (f(b)) Right(b) else Left(None))

  /**
   * Transforms the `get` value of the `ZRef` with the specified function.
   */
  final def map[C](f: B => C): ZRef[EA, EB, A, C] =
    mapEither(b => Right(f(b)))

  /**
   * Transforms the `get` value of the `ZRef` with the specified fallible
   * function.
   */
  final def mapEither[EC >: EB, C](f: B => Either[EC, C]): ZRef[EA, EC, A, C] =
    dimapEither(Right(_), f)

  /**
   * Returns a read only view of the `ZRef`.
   */
  final def readOnly: ZRef[EA, EB, Nothing, B] =
    self

  /**
   * Returns a write only view of the `ZRef`.
   */
  final def writeOnly: ZRef[EA, Unit, A, Nothing] =
    fold(identity, _ => (), Right(_), _ => Left(()))
}

object ZRef extends Serializable {

  private final case class Atomic[A](value: AtomicReference[A]) extends Ref[A] { self =>

    def fold[EC, ED, C, D](
      ea: Nothing => EC,
      eb: Nothing => ED,
      ca: C => Either[EC, A],
      bd: A => Either[ED, D]
    ): ZRef[EC, ED, C, D] =
      new Derived[EC, ED, C, D] {
        type S = A
        def getEither(s: S): Either[ED, D] = bd(s)
        def setEither(c: C): Either[EC, S] = ca(c)
        val value: Atomic[S]               = self
      }

    def foldAll[EC, ED, C, D](
      ea: Nothing => EC,
      eb: Nothing => ED,
      ec: Nothing => EC,
      ca: C => A => Either[EC, A],
      bd: A => Either[ED, D]
    ): ZRef[EC, ED, C, D] =
      new DerivedAll[EC, ED, C, D] {
        type S = A
        def getEither(s: S): Either[ED, D]       = bd(s)
        def setEither(c: C)(s: S): Either[EC, S] = ca(c)(s)
        val value: Atomic[S]                     = self
      }

    def get: UIO[A] =
      UIO.effectTotal(value.get)

    def getAndSet(a: A): UIO[A] =
      UIO.effectTotal {
        var loop       = true
        var current: A = null.asInstanceOf[A]
        while (loop) {
          current = value.get
          loop = !value.compareAndSet(current, a)
        }
        current
      }

    def getAndUpdate(f: A => A): UIO[A] =
      UIO.effectTotal {
        var loop       = true
        var current: A = null.asInstanceOf[A]
        while (loop) {
          current = value.get
          val next = f(current)
          loop = !value.compareAndSet(current, next)
        }
        current
      }

    def getAndUpdateSome(pf: PartialFunction[A, A]): UIO[A] =
      UIO.effectTotal {
        var loop       = true
        var current: A = null.asInstanceOf[A]
        while (loop) {
          current = value.get
          val next = pf.applyOrElse(current, (_: A) => current)
          loop = !value.compareAndSet(current, next)
        }
        current
      }

    def modify[B](f: A => (B, A)): UIO[B] =
      UIO.effectTotal {
        var loop = true
        var b: B = null.asInstanceOf[B]
        while (loop) {
          val current = value.get
          val tuple   = f(current)
          b = tuple._1
          loop = !value.compareAndSet(current, tuple._2)
        }
        b
      }

    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): UIO[B] =
      UIO.effectTotal {
        var loop = true
        var b: B = null.asInstanceOf[B]
        while (loop) {
          val current = value.get
          val tuple   = pf.applyOrElse(current, (_: A) => (default, current))
          b = tuple._1
          loop = !value.compareAndSet(current, tuple._2)
        }
        b
      }

    def set(a: A): UIO[Unit] =
      UIO.effectTotal(value.set(a))

    def setAsync(a: A): UIO[Unit] =
      UIO.effectTotal(value.lazySet(a))

    override def toString: String =
      s"Ref(${value.get})"

    def update(f: A => A): UIO[Unit] =
      UIO.effectTotal {
        var loop    = true
        var next: A = null.asInstanceOf[A]
        while (loop) {
          val current = value.get
          next = f(current)
          loop = !value.compareAndSet(current, next)
        }
        ()
      }

    def updateAndGet(f: A => A): UIO[A] =
      UIO.effectTotal {
        var loop    = true
        var next: A = null.asInstanceOf[A]
        while (loop) {
          val current = value.get
          next = f(current)
          loop = !value.compareAndSet(current, next)
        }
        next
      }

    def updateSome(pf: PartialFunction[A, A]): UIO[Unit] =
      UIO.effectTotal {
        var loop    = true
        var next: A = null.asInstanceOf[A]
        while (loop) {
          val current = value.get
          next = pf.applyOrElse(current, (_: A) => current)
          loop = !value.compareAndSet(current, next)
        }
        ()
      }

    def updateSomeAndGet(pf: PartialFunction[A, A]): UIO[A] =
      UIO.effectTotal {
        var loop    = true
        var next: A = null.asInstanceOf[A]
        while (loop) {
          val current = value.get
          next = pf.applyOrElse(current, (_: A) => current)
          loop = !value.compareAndSet(current, next)
        }
        next
      }

    def unsafeUpdate(f: A => A): Unit = {
      var loop    = true
      var next: A = null.asInstanceOf[A]
      while (loop) {
        val current = value.get
        next = f(current)
        loop = !value.compareAndSet(current, next)
      }
    }

  }

  private trait Derived[+EA, +EB, -A, +B] extends ZRef[EA, EB, A, B] { self =>
    type S

    def getEither(s: S): Either[EB, B]

    def setEither(a: A): Either[EA, S]

    val value: Atomic[S]

    final def fold[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ca: C => Either[EC, A],
      bd: B => Either[ED, D]
    ): ZRef[EC, ED, C, D] =
      new Derived[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bd)
        def setEither(c: C): Either[EC, S] =
          ca(c).flatMap(a => self.setEither(a).fold(e => Left(ea(e)), Right(_)))
        val value: Atomic[S] =
          self.value
      }

    final def foldAll[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ec: EB => EC,
      ca: C => B => Either[EC, A],
      bd: B => Either[ED, D]
    ): ZRef[EC, ED, C, D] =
      new DerivedAll[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bd)
        def setEither(c: C)(s: S): Either[EC, S] =
          self
            .getEither(s)
            .fold(e => Left(ec(e)), ca(c))
            .flatMap(a => self.setEither(a).fold(e => Left(ea(e)), Right(_)))
        val value: Atomic[S] =
          self.value
      }

    final def get: IO[EB, B] =
      value.get.flatMap(getEither(_).fold(ZIO.fail(_), ZIO.succeedNow))

    final def set(a: A): IO[EA, Unit] =
      setEither(a).fold(ZIO.fail(_), value.set)

    final def setAsync(a: A): IO[EA, Unit] =
      setEither(a).fold(ZIO.fail(_), value.setAsync)
  }

  private trait DerivedAll[+EA, +EB, -A, +B] extends ZRef[EA, EB, A, B] { self =>
    type S

    def getEither(s: S): Either[EB, B]

    def setEither(a: A)(s: S): Either[EA, S]

    val value: Atomic[S]

    final def fold[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ca: C => Either[EC, A],
      bd: B => Either[ED, D]
    ): ZRef[EC, ED, C, D] =
      new DerivedAll[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bd)
        def setEither(c: C)(s: S): Either[EC, S] =
          ca(c).flatMap(a => self.setEither(a)(s).fold(e => Left(ea(e)), Right(_)))
        val value: Atomic[S] =
          self.value
      }

    final def foldAll[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ec: EB => EC,
      ca: C => B => Either[EC, A],
      bd: B => Either[ED, D]
    ): ZRef[EC, ED, C, D] =
      new DerivedAll[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bd)
        def setEither(c: C)(s: S): Either[EC, S] =
          self
            .getEither(s)
            .fold(e => Left(ec(e)), ca(c))
            .flatMap(a => self.setEither(a)(s).fold(e => Left(ea(e)), Right(_)))
        val value: Atomic[S] =
          self.value
      }

    final def get: IO[EB, B] =
      value.get.flatMap(getEither(_).fold(ZIO.fail(_), ZIO.succeedNow))

    final def set(a: A): IO[EA, Unit] =
      value.modify { s =>
        setEither(a)(s) match {
          case Left(e)  => (Left(e), s)
          case Right(s) => (Right(()), s)
        }
      }.absolve

    final def setAsync(a: A): IO[EA, Unit] =
      value.modify { s =>
        setEither(a)(s) match {
          case Left(e)  => (Left(e), s)
          case Right(s) => (Right(()), s)
        }
      }.absolve
  }

  implicit class UnifiedSyntax[+E, A](private val self: ERef[E, A]) extends AnyVal {

    /**
     * Atomically writes the specified value to the `ZRef`, returning the value
     * immediately before modification.
     */
    def getAndSet(a: A): IO[E, A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndSet(a)
        case derived           => derived.modify(v => (v, a))
      }

    /**
     * Atomically modifies the `ZRef` with the specified function, returning
     * the value immediately before modification.
     */
    def getAndUpdate(f: A => A): IO[E, A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndUpdate(f)
        case derived           => derived.modify(v => (v, f(v)))
      }

    /**
     * Atomically modifies the `ZRef` with the specified partial function,
     * returning the value immediately before modification. If the function is
     * undefined on the current value it doesn't change it.
     */
    def getAndUpdateSome(pf: PartialFunction[A, A]): IO[E, A] =
      self match {
        case atomic: Atomic[A] => atomic.getAndUpdateSome(pf)
        case derived =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            (v, result)
          }
      }

    /**
     * Atomically modifies the `ZRef` with the specified function, which
     * computes a return value for the modification. This is a more powerful
     * version of `update`.
     */
    def modify[B](f: A => (B, A)): IO[E, B] =
      self match {
        case atomic: Atomic[A] => atomic.modify(f)
        case derived: Derived[E, E, A, A] =>
          derived.value.modify { s =>
            derived.getEither(s) match {
              case Left(e) => (Left(e), s)
              case Right(a1) => {
                val (b, a2) = f(a1)
                derived.setEither(a2) match {
                  case Left(e)  => (Left(e), s)
                  case Right(s) => (Right(b), s)
                }
              }
            }
          }.absolve
        case derivedAll: DerivedAll[E, E, A, A] =>
          derivedAll.value.modify { s =>
            derivedAll.getEither(s) match {
              case Left(e) => (Left(e), s)
              case Right(a1) => {
                val (b, a2) = f(a1)
                derivedAll.setEither(a2)(s) match {
                  case Left(e)  => (Left(e), s)
                  case Right(s) => (Right(b), s)
                }
              }
            }
          }.absolve
      }

    /**
     * Atomically modifies the `ZRef` with the specified partial function,
     * which computes a return value for the modification if the function is
     * defined on the current value otherwise it returns a default value. This
     * is a more powerful version of `updateSome`.
     */
    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): IO[E, B] =
      self match {
        case atomic: Atomic[A] => atomic.modifySome(default)(pf)
        case derived =>
          derived.modify(v => pf.applyOrElse[A, (B, A)](v, _ => (default, v)))
      }

    /**
     * Atomically modifies the `ZRef` with the specified function.
     */
    def update(f: A => A): IO[E, Unit] =
      self match {
        case atomic: Atomic[A] => atomic.update(f)
        case derived           => derived.modify(v => ((), f(v)))
      }

    /**
     * Atomically modifies the `ZRef` with the specified function and returns
     * the updated value.
     */
    def updateAndGet(f: A => A): IO[E, A] =
      self match {
        case atomic: Atomic[A] => atomic.updateAndGet(f)
        case derived =>
          derived.modify { v =>
            val result = f(v)
            (result, result)
          }
      }

    /**
     * Atomically modifies the `ZRef` with the specified partial function. If
     * the function is undefined on the current value it doesn't change it.
     */
    def updateSome(pf: PartialFunction[A, A]): IO[E, Unit] =
      self match {
        case atomic: Atomic[A] => atomic.updateSome(pf)
        case derived =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            ((), result)
          }
      }

    /**
     * Atomically modifies the `ZRef` with the specified partial function. If
     * the function is undefined on the current value it returns the old value
     * without changing it.
     */
    def updateSomeAndGet(pf: PartialFunction[A, A]): IO[E, A] =
      self match {
        case atomic: Atomic[A] => atomic.updateSomeAndGet(pf)
        case derived =>
          derived.modify { v =>
            val result = pf.applyOrElse[A, A](v, identity)
            (result, result)
          }
      }

  }

  implicit class UnsafeSyntax[A](private val self: Ref[A]) extends AnyVal {

    private[zio] def unsafeUpdate(f: A => A): Unit =
      self match {
        case atomic: Atomic[A] => atomic.unsafeUpdate(f)
        case derived: Derived[Nothing, Nothing, A, A] =>
          derived.value.unsafeUpdate(s => derived.setEither(f(derived.getEither(s).merge)).merge)
        case derivedAll: DerivedAll[Nothing, Nothing, A, A] =>
          derivedAll.value.unsafeUpdate(s => derivedAll.setEither(f(derivedAll.getEither(s).merge))(s).merge)
      }
  }

  /**
   * Creates a new `ZRef` with the specified value.
   */
  def make[A](a: A): UIO[Ref[A]] =
    UIO.effectTotal(Atomic(new AtomicReference(a)))

  /**
   * Creates a new managed `ZRef` with the specified value
   */
  def makeManaged[A](a: A): Managed[Nothing, Ref[A]] =
    make(a).toManaged_
}
