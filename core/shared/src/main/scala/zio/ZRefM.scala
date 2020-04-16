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

package zio

/**
 * A `ZRefM[RA, RB, EA, EB, A, B]` is a polymorphic, purely functional
 * description of a mutable reference. The fundamental operations of a `ZRefM`
 * are `set` and `get`. `set` takes a value of type `A` and sets the reference
 * to a new value, requiring an environment of type `RA` and potentially
 * failing with an error of type `EA`. `get` gets the current value of the
 * reference and returns a value of type `B`, requiring an environment of type
 * `RB` and potentially failing with an error of type `EB`.
 *
 * When the error and value types of the `ZRefM` are unified, that is, it is a
 * `ZRefM[E, E, A, A]`, the `ZRefM` also supports atomic `modify` and `update`
 * operations.
 *
 * Unlike `ZRef`, `ZRefM` allows performing effects within update operations,
 * at some cost to performance. Writes will semantically block other writers,
 * while multiple readers can read simultaneously.
 */
sealed trait ZRefM[-RA, -RB, +EA, +EB, -A, +B] { self =>

  /**
   * Folds over the error and value types of the `ZRefM`. This is a highly
   * polymorphic method that is capable of arbitrarily transforming the error
   * and value types of the `ZRefM`. For most use cases one of the more
   * specific combinators implemented in terms of `foldM` will be more
   * ergonomic but this method is extremely useful for implementing new
   * combinators.
   */
  def foldM[RC <: RA, RD <: RB, EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ca: C => ZIO[RC, EC, A],
    bd: B => ZIO[RD, ED, D]
  ): ZRefM[RC, RD, EC, ED, C, D]

  /**
   * Folds over the error and value types of the `ZRefM`, allowing access to
   * the state in transforming the `set` value. This is a more powerful version
   * of `foldM` but requires unifying the environment and error types.
   */
  def foldAllM[RC <: RA with RB, RD <: RB, EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ec: EB => EC,
    ca: C => B => ZIO[RC, EC, A],
    bd: B => ZIO[RD, ED, D]
  ): ZRefM[RC, RD, EC, ED, C, D]

  /**
   * Reads the value from the `ZRefM`.
   */
  def get: ZIO[RB, EB, B]

  /**
   * Writes a new value to the `ZRefM`, with a guarantee of immediate
   * consistency (at some cost to performance).
   */
  def set(a: A): ZIO[RA, EA, Unit]

  /**
   * Writes a new value to the `ZRefM` without providing a guarantee of
   * immediate consistency.
   */
  def setAsync(a: A): ZIO[RA, EA, Unit]

  /**
   * Maps and filters the `get` value of the `ZRefM` with the specified partial
   * function, returning a `ZRefM` with a `get` value that succeeds with the
   * result of the partial function if it is defined or else fails with `None`.
   */
  final def collect[C](pf: PartialFunction[B, C]): ZRefM[RA, RB, EA, Option[EB], A, C] =
    collectM(pf andThen ZIO.succeedNow)

  /**
   * Maps and filters the `get` value of the `ZRefM` with the specified
   * effectual partial function, returning a `ZRefM` with a `get` value that
   * succeeds with the result of the partial function if it is defined or else
   * fails with `None`.
   */
  final def collectM[RC <: RB, EC >: EB, C](
    pf: PartialFunction[B, ZIO[RC, EC, C]]
  ): ZRefM[RA, RC, EA, Option[EC], A, C] =
    foldM(
      identity,
      Some(_),
      ZIO.succeedNow,
      b => pf.andThen(_.asSomeError).applyOrElse[B, ZIO[RC, Option[EC], C]](b, _ => ZIO.fail(None))
    )

  /**
   * Transforms the `set` value of the `ZRefM` with the specified function.
   */
  final def contramap[C](f: C => A): ZRefM[RA, RB, EA, EB, C, B] =
    contramapM(c => ZIO.succeedNow(f(c)))

  /**
   * Transforms the `set` value of the `ZRefM` with the specified effectual
   * function.
   */
  final def contramapM[RC <: RA, EC >: EA, C](f: C => ZIO[RC, EC, A]): ZRefM[RC, RB, EC, EB, C, B] =
    dimapM(f, ZIO.succeedNow)

  /**
   * Transforms both the `set` and `get` values of the `ZRefM` with the
   * specified functions.
   */
  final def dimap[C, D](f: C => A, g: B => D): ZRefM[RA, RB, EA, EB, C, D] =
    dimapM(c => ZIO.succeedNow(f(c)), b => ZIO.succeedNow(g(b)))

  /**
   * Transforms both the `set` and `get` values of the `ZRefM` with the
   * specified effectual functions.
   */
  final def dimapM[RC <: RA, RD <: RB, EC >: EA, ED >: EB, C, D](
    f: C => ZIO[RC, EC, A],
    g: B => ZIO[RD, ED, D]
  ): ZRefM[RC, RD, EC, ED, C, D] =
    foldM(identity, identity, f, g)

  /**
   * Transforms both the `set` and `get` errors of the `ZRefM` with the
   * specified functions.
   */
  final def dimapError[EC, ED](f: EA => EC, g: EB => ED): ZRefM[RA, RB, EC, ED, A, B] =
    fold(f, g, Right(_), Right(_))

  /**
   * Filters the `set` value of the `ZRefM` with the specified predicate,
   * returning a `ZRefM` with a `set` value that succeeds if the predicate is
   * satisfied or else fails with `None`.
   */
  final def filterInput[A1 <: A](f: A1 => Boolean): ZRefM[RA, RB, Option[EA], EB, A1, B] =
    filterInputM(a => ZIO.succeedNow(f(a)))

  /**
   * Filters the `set` value of the `ZRefM` with the specified effectual
   * predicate, returning a `ZRefM` with a `set` value that succeeds if the
   * predicate is satisfied or else fails with `None`.
   */
  final def filterInputM[RC <: RA, EC >: EA, A1 <: A](
    f: A1 => ZIO[RC, EC, Boolean]
  ): ZRefM[RC, RB, Option[EC], EB, A1, B] =
    foldM(Some(_), identity, a => ZIO.ifM(f(a).asSomeError)(ZIO.succeedNow(a), ZIO.fail(None)), ZIO.succeedNow)

  /**
   * Filters the `get` value of the `ZRefM` with the specified predicate,
   * returning a `ZRefM` with a `get` value that succeeds if the predicate is
   * satisfied or else fails with `None`.
   */
  final def filterOutput(f: B => Boolean): ZRefM[RA, RB, EA, Option[EB], A, B] =
    filterOutputM(a => ZIO.succeedNow(f(a)))

  /**
   * Filters the `get` value of the `ZRefM` with the specified effectual predicate,
   * returning a `ZRefM` with a `get` value that succeeds if the predicate is
   * satisfied or else fails with `None`.
   */
  final def filterOutputM[RC <: RB, EC >: EB](f: B => ZIO[RC, EC, Boolean]): ZRefM[RA, RC, EA, Option[EC], A, B] =
    foldM(identity, Some(_), ZIO.succeedNow, b => ZIO.ifM(f(b).asSomeError)(ZIO.succeedNow(b), ZIO.fail(None)))

  /**
   * Folds over the error and value types of the `ZRefM`.
   */
  def fold[EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ca: C => Either[EC, A],
    bd: B => Either[ED, D]
  ): ZRefM[RA, RB, EC, ED, C, D] =
    foldM(ea, eb, c => ZIO.fromEither(ca(c)), b => ZIO.fromEither(bd(b)))

  /**
   * Folds over the error and value types of the `ZRefM`, allowing access to
   * the state in transforming the `set` value but requiring unifying the error
   * type.
   */
  def foldAll[EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ec: EB => EC,
    ca: C => B => Either[EC, A],
    bd: B => Either[ED, D]
  ): ZRefM[RA with RB, RB, EC, ED, C, D] =
    foldAllM(ea, eb, ec, c => b => ZIO.fromEither(ca(c)(b)), b => ZIO.fromEither(bd(b)))

  /**
   * Transforms the `get` value of the `ZRefM` with the specified function.
   */
  final def map[C](f: B => C): ZRefM[RA, RB, EA, EB, A, C] =
    mapM(b => ZIO.succeedNow(f(b)))

  /**
   * Transforms the `get` value of the `ZRefM` with the specified effectual
   * function.
   */
  final def mapM[RC <: RB, EC >: EB, C](f: B => ZIO[RC, EC, C]): ZRefM[RA, RC, EA, EC, A, C] =
    dimapM(ZIO.succeedNow, f)

  /**
   * Returns a read only view of the `ZRefM`.
   */
  final def readOnly: ZRefM[RA, RB, EA, EB, Nothing, B] =
    self

  /**
   * Performs the specified effect every time a value is written to this
   * `ZRefM`.
   */
  final def tapInput[RC <: RA, EC >: EA, A1 <: A](f: A1 => ZIO[RC, EC, Any]): ZRefM[RC, RB, EC, EB, A1, B] =
    contramapM(a => f(a).as(a))

  /**
   * Performs the specified effect very time a value is read from this
   * `ZRefM`.
   */
  final def tapOutput[RC <: RB, EC >: EB](f: B => ZIO[RC, EC, Any]): ZRefM[RA, RC, EA, EC, A, B] =
    mapM(b => f(b).as(b))

  /**
   * Returns a write only view of the `ZRefM`.
   */
  final def writeOnly: ZRefM[RA, RB, EA, Unit, A, Nothing] =
    fold(identity, _ => (), Right(_), _ => Left(()))
}

object ZRefM {

  private final case class Atomic[A](ref: Ref[A], semaphore: Semaphore) extends RefM[A] { self =>

    def foldM[RC, RD, EC, ED, C, D](
      ea: Nothing => EC,
      eb: Nothing => ED,
      ca: C => ZIO[RC, EC, A],
      bd: A => ZIO[RD, ED, D]
    ): ZRefM[RC, RD, EC, ED, C, D] =
      new Derived[RC, RD, EC, ED, C, D] {
        type S = A
        def getEither(s: S): ZIO[RD, ED, D] = bd(s)
        def setEither(c: C): ZIO[RC, EC, S] = ca(c)
        val value: Atomic[S]                = self
      }

    def foldAllM[RC, RD, EC, ED, C, D](
      ea: Nothing => EC,
      eb: Nothing => ED,
      ec: Nothing => EC,
      ca: C => A => ZIO[RC, EC, A],
      bd: A => ZIO[RD, ED, D]
    ): ZRefM[RC, RD, EC, ED, C, D] =
      new DerivedAll[RC, RD, EC, ED, C, D] {
        type S = A
        def getEither(s: S): ZIO[RD, ED, D]       = bd(s)
        def setEither(c: C)(s: S): ZIO[RC, EC, S] = ca(c)(s)
        val value: Atomic[S]                      = self
      }

    def get: UIO[A] =
      ref.get

    def set(a: A): UIO[Unit] =
      semaphore.withPermit(ref.set(a))

    def setAsync(a: A): UIO[Unit] =
      semaphore.withPermit(ref.setAsync(a))
  }

  private trait Derived[-RA, -RB, +EA, +EB, -A, +B] extends ZRefM[RA, RB, EA, EB, A, B] { self =>
    type S

    def getEither(s: S): ZIO[RB, EB, B]

    def setEither(a: A): ZIO[RA, EA, S]

    val value: Atomic[S]

    def foldM[RC <: RA, RD <: RB, EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ca: C => ZIO[RC, EC, A],
      bd: B => ZIO[RD, ED, D]
    ): ZRefM[RC, RD, EC, ED, C, D] =
      new Derived[RC, RD, EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): ZIO[RD, ED, D] =
          self.getEither(s).foldM(e => ZIO.fail(eb(e)), bd)
        def setEither(c: C): ZIO[RC, EC, S] =
          ca(c).flatMap(a => self.setEither(a).mapError(ea))
        val value: Atomic[S] =
          self.value
      }

    def foldAllM[RC <: RA with RB, RD <: RB, EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ec: EB => EC,
      ca: C => B => ZIO[RC, EC, A],
      bd: B => ZIO[RD, ED, D]
    ): ZRefM[RC, RD, EC, ED, C, D] =
      new DerivedAll[RC, RD, EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): ZIO[RD, ED, D] =
          self.getEither(s).foldM(e => ZIO.fail(eb(e)), bd)
        def setEither(c: C)(s: S): ZIO[RC, EC, S] =
          self
            .getEither(s)
            .foldM(e => ZIO.fail(ec(e)), ca(c))
            .flatMap(a => self.setEither(a).mapError(ea))
        val value: Atomic[S] =
          self.value
      }

    def get: ZIO[RB, EB, B] =
      value.get.flatMap(getEither)

    def set(a: A): ZIO[RA, EA, Unit] =
      value.semaphore.withPermit(setEither(a).flatMap(value.ref.set))

    def setAsync(a: A): ZIO[RA, EA, Unit] =
      value.semaphore.withPermit(setEither(a).flatMap(value.ref.setAsync))
  }

  private trait DerivedAll[-RA, -RB, +EA, +EB, -A, +B] extends ZRefM[RA, RB, EA, EB, A, B] { self =>
    type S

    def getEither(s: S): ZIO[RB, EB, B]

    def setEither(a: A)(s: S): ZIO[RA, EA, S]

    val value: Atomic[S]

    def foldM[RC <: RA, RD <: RB, EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ca: C => ZIO[RC, EC, A],
      bd: B => ZIO[RD, ED, D]
    ): ZRefM[RC, RD, EC, ED, C, D] =
      new DerivedAll[RC, RD, EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): ZIO[RD, ED, D] =
          self.getEither(s).foldM(e => ZIO.fail(eb(e)), bd)
        def setEither(c: C)(s: S): ZIO[RC, EC, S] =
          ca(c).flatMap(a => self.setEither(a)(s).mapError(ea))
        val value: Atomic[S] =
          self.value
      }

    def foldAllM[RC <: RA with RB, RD <: RB, EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ec: EB => EC,
      ca: C => B => ZIO[RC, EC, A],
      bd: B => ZIO[RD, ED, D]
    ): ZRefM[RC, RD, EC, ED, C, D] =
      new DerivedAll[RC, RD, EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): ZIO[RD, ED, D] =
          self.getEither(s).foldM(e => ZIO.fail(eb(e)), bd)
        def setEither(c: C)(s: S): ZIO[RC, EC, S] =
          self
            .getEither(s)
            .foldM(e => ZIO.fail(ec(e)), ca(c))
            .flatMap(a => self.setEither(a)(s).mapError(ea))
        val value: Atomic[S] =
          self.value
      }

    def get: ZIO[RB, EB, B] =
      value.get.flatMap(getEither)

    def set(a: A): ZIO[RA, EA, Unit] =
      value.semaphore.withPermit(value.get.flatMap(setEither(a)).flatMap(value.ref.set))

    def setAsync(a: A): ZIO[RA, EA, Unit] =
      value.semaphore.withPermit(value.get.flatMap(setEither(a)).flatMap(value.ref.setAsync))
  }

  implicit class UnifiedSyntax[-R, +E, A](private val self: ZRefM[R, R, E, E, A, A]) extends AnyVal {

    /**
     * Writes a new value to the `RefM`, returning the value immediately before
     * modification.
     */
    def getAndSet(a: A): ZIO[R, E, A] =
      modify(v => ZIO.succeedNow((v, a)))

    /**
     * Atomically modifies the `RefM` with the specified function, returning the
     * value immediately before modification.
     */
    def getAndUpdate[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, A]): ZIO[R1, E1, A] =
      modify(v => f(v).map(result => (v, result)))

    /**
     * Atomically modifies the `RefM` with the specified partial function,
     * returning the value immediately before modification.
     * If the function is undefined on the current value it doesn't change it.
     */
    def getAndUpdateSome[R1 <: R, E1 >: E](pf: PartialFunction[A, ZIO[R1, E1, A]]): ZIO[R1, E1, A] =
      modify(v => pf.applyOrElse[A, ZIO[R1, E1, A]](v, ZIO.succeedNow).map(result => (v, result)))

    /**
     * Atomically modifies the `RefM` with the specified function, which computes
     * a return value for the modification. This is a more powerful version of
     * `update`.
     */
    def modify[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, (B, A)]): ZIO[R1, E1, B] =
      self match {
        case atomic: Atomic[A] =>
          atomic.semaphore.withPermit(atomic.ref.get.flatMap(f).flatMap { case (b, a) => atomic.ref.set(a).as(b) })
        case derived: Derived[R, R, E, E, A, A] =>
          derived.value.semaphore.withPermit {
            derived.value.ref.get.flatMap { s =>
              derived.getEither(s).flatMap(f).flatMap {
                case (b, a) =>
                  derived.setEither(a).flatMap(derived.value.ref.set).as(b)
              }
            }
          }
        case derivedAll: DerivedAll[R, R, E, E, A, A] =>
          derivedAll.value.semaphore.withPermit {
            derivedAll.value.ref.get.flatMap { s =>
              derivedAll.getEither(s).flatMap(f).flatMap {
                case (b, a) =>
                  derivedAll.setEither(a)(s).flatMap(derivedAll.value.ref.set).as(b)
              }
            }
          }
      }

    /**
     * Atomically modifies the `RefM` with the specified function, which computes
     * a return value for the modification if the function is defined in the current value
     * otherwise it returns a default value.
     * This is a more powerful version of `updateSome`.
     */
    def modifySome[R1 <: R, E1 >: E, B](default: B)(pf: PartialFunction[A, ZIO[R1, E1, (B, A)]]): ZIO[R1, E1, B] =
      modify(v => pf.applyOrElse[A, ZIO[R1, E1, (B, A)]](v, _ => ZIO.succeedNow((default, v))))

    /**
     * Atomically modifies the `RefM` with the specified function.
     */
    def update[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, A]): ZIO[R1, E1, Unit] =
      modify(v => f(v).map(result => ((), result)))

    /**
     * Atomically modifies the `RefM` with the specified function, returning the
     * value immediately after modification.
     */
    def updateAndGet[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, A]): ZIO[R1, E1, A] =
      modify(v => f(v).map(result => (result, result)))

    /**
     * Atomically modifies the `RefM` with the specified partial function.
     * If the function is undefined on the current value it doesn't change it.
     */
    def updateSome[R1 <: R, E1 >: E](pf: PartialFunction[A, ZIO[R1, E1, A]]): ZIO[R1, E1, Unit] =
      modify(v => pf.applyOrElse[A, ZIO[R1, E1, A]](v, ZIO.succeedNow).map(result => ((), result)))

    /**
     * Atomically modifies the `RefM` with the specified partial function.
     * If the function is undefined on the current value it returns the old value
     * without changing it.
     */
    def updateSomeAndGet[R1 <: R, E1 >: E](pf: PartialFunction[A, ZIO[R1, E1, A]]): ZIO[R1, E1, A] =
      modify(v => pf.applyOrElse[A, ZIO[R1, E1, A]](v, ZIO.succeedNow).map(result => (result, result)))
  }

  /**
   * Creates a new `ZRefM` with the specified value.
   */
  def make[A](a: A): UIO[RefM[A]] =
    for {
      ref       <- Ref.make(a)
      semaphore <- Semaphore.make(1)
    } yield Atomic(ref, semaphore)

  /**
   * Creates a new `ZRefM` with the specified value in the context of a
   * `Managed.`
   */
  def makeManaged[A](a: A): UManaged[RefM[A]] =
    make(a).toManaged_

  /**
   * Creates a new `ZRefM` and a `Queue` that will contain every value written
   * to the `ZRefM`.
   */
  def signalRef[A](a: A): UIO[(RefM[A], Queue[A])] =
    for {
      ref   <- make(a)
      queue <- Queue.unbounded[A]
    } yield (ref.tapInput(queue.offer), queue)
}
