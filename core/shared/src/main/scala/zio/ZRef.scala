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

sealed trait ZRef[+EA, +EB, -A, +B] extends Serializable { self =>

  def get: IO[EB, B]

  def fold[EC, ED, C, D](
    ea: EA => EC,
    eb: EB => ED,
    ca: C => Either[EC, A],
    bc: B => Either[ED, D]
  ): ZRef[EC, ED, C, D]

  def set(a: A): IO[EA, Unit]

  def setAsync(a: A): IO[EA, Unit]

  final def collect[C](pf: PartialFunction[B, C]): ZRef[EA, Option[EB], A, C] =
    fold(identity, Some(_), Right(_), pf.lift(_).fold[Either[Option[EB], C]](Left(None))(Right(_)))

  final def contramap[C](f: C => A): ZRef[EA, EB, C, B] =
    contramapEither(c => Right(f(c)))

  final def contramapEither[EC >: EA, C](f: C => Either[EC, A]): ZRef[EC, EB, C, B] =
    dimapEither(f, Right(_))

  final def dimap[C, D](f: C => A, g: B => D): ZRef[EA, EB, C, D] =
    dimapEither(c => Right(f(c)), b => Right(g(b)))

  final def dimapEither[EC >: EA, ED >: EB, C, D](f: C => Either[EC, A], g: B => Either[ED, D]): ZRef[EC, ED, C, D] =
    fold(identity, identity, f, g)

  final def dimapError[EC, ED](f: EA => EC, g: EB => ED): ZRef[EC, ED, A, B] =
    fold(f, g, Right(_), Right(_))

  final def map[C](f: B => C): ZRef[EA, EB, A, C] =
    mapEither(b => Right(f(b)))

  final def mapEither[EC >: EB, C](f: B => Either[EC, C]): ZRef[EA, EC, A, C] =
    dimapEither(Right(_), f)

  final def unifyError[E](ea: EA => E, eb: EB => E): ZRef[E, E, A, B] =
    dimapError(ea, eb)

  final def unifyValue[C](ca: C => A, bc: B => C): ZRef[EA, EB, C, C] =
    dimap(ca, bc)
}

object ZRef extends Serializable {

  private final case class Atomic[A](value: AtomicReference[A]) extends ZRef[Nothing, Nothing, A, A] { self =>

    def fold[EC, ED, C, D](
      ea: Nothing => EC,
      eb: Nothing => ED,
      ca: C => Either[EC, A],
      bc: A => Either[ED, D]
    ): ZRef[EC, ED, C, D] =
      new Derived[EC, ED, C, D] {
        type S = A
        def getEither(s: S): Either[ED, D] = bc(s)
        def setEither(c: C): Either[EC, S] = ca(c)
        val value: Atomic[S]               = self
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

          val tuple = f(current)

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

          val tuple = pf.applyOrElse(current, (_: A) => (default, current))

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
  }

  private trait Derived[+EA, +EB, -A, +B] extends ZRef[EA, EB, A, B] { self =>
    type S

    def getEither(s: S): Either[EB, B]

    def setEither(a: A): Either[EA, S]

    val value: Atomic[S]

    final def get: IO[EB, B] =
      value.get.flatMap(getEither(_).fold(ZIO.fail(_), ZIO.succeedNow))

    final def fold[EC, ED, C, D](
      ea: EA => EC,
      eb: EB => ED,
      ca: C => Either[EC, A],
      bc: B => Either[ED, D]
    ): ZRef[EC, ED, C, D] =
      new Derived[EC, ED, C, D] {
        type S = self.S
        def getEither(s: S): Either[ED, D] =
          self.getEither(s).fold(e => Left(eb(e)), bc)
        def setEither(c: C): Either[EC, S] =
          ca(c).flatMap(a => self.setEither(a).fold(e => Left(ea(e)), Right(_)))
        val value: Atomic[S] =
          self.value
      }

    final def set(a: A): IO[EA, Unit] =
      setEither(a).fold(ZIO.fail(_), value.set)

    final def setAsync(a: A): IO[EA, Unit] =
      setEither(a).fold(ZIO.fail(_), value.setAsync)
  }

  implicit class UnifiedSyntax[E, A](private val self: ZRef[E, E, A, A]) extends AnyVal {
    def getAndSet(a: A): IO[E, A] = self match {
      case atomic: Atomic[A]            => atomic.getAndSet(a)
      case derived: Derived[E, E, A, A] => derived.modify(v => (v, a))
    }
    def getAndUpdate(f: A => A): IO[E, A] = self match {
      case atomic: Atomic[A]            => atomic.getAndUpdate(f)
      case derived: Derived[E, E, A, A] => derived.modify(v => (v, f(v)))
    }
    def getAndUpdateSome(pf: PartialFunction[A, A]): IO[E, A] = self match {
      case atomic: Atomic[A] => atomic.getAndUpdateSome(pf)
      case derived: Derived[E, E, A, A] =>
        derived.modify { v =>
          val result = pf.applyOrElse[A, A](v, identity)
          (v, result)
        }
    }
    def modify[B](f: A => (B, A)): IO[E, B] = self match {
      case atomic: Atomic[A] => atomic.modify(f)
      case derived: Derived[E, E, A, A] =>
        derived.value.modify { s =>
          derived.getEither(s) match {
            case Left(e) => (Left(e), s)
            case Right(b) => {
              val (c, a) = f(b)
              derived.setEither(a) match {
                case Left(e)  => (Left(e), s)
                case Right(s) => (Right(c), s)
              }
            }
          }
        }.absolve
    }
    def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): IO[E, B] = self match {
      case atomic: Atomic[A] => atomic.modifySome(default)(pf)
      case derived: Derived[E, E, A, A] =>
        derived.modify(v => pf.applyOrElse[A, (B, A)](v, _ => (default, v)))
    }
    def update(f: A => A): IO[E, Unit] = self match {
      case atomic: Atomic[A]            => atomic.update(f)
      case derived: Derived[E, E, A, A] => derived.modify(v => ((), v))
    }
    def updateAndGet(f: A => A): IO[E, A] = self match {
      case atomic: Atomic[A] => atomic.updateAndGet(f)
      case derived: Derived[E, E, A, A] =>
        derived.modify { v =>
          val result = f(v)
          (result, result)
        }
    }
    def updateSome(pf: PartialFunction[A, A]): IO[E, Unit] = self match {
      case atomic: Atomic[A] => atomic.updateSome(pf)
      case derived: Derived[E, E, A, A] =>
        derived.modify { v =>
          val result = pf.applyOrElse[A, A](v, identity)
          ((), result)
        }
    }
    def updateSomeAndGet(pf: PartialFunction[A, A]): IO[E, A] = self match {
      case atomic: Atomic[A] => atomic.updateSomeAndGet(pf)
      case derived: Derived[E, E, A, A] =>
        derived.modify { v =>
          val result = pf.applyOrElse[A, A](v, identity)
          (result, result)
        }
    }
  }

  def make[A](a: A): UIO[Ref[A]] =
    UIO.effectTotal(Atomic(new AtomicReference(a)))
}
