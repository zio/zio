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

/**
 * An `Exit[E, A]` describes the result of executing an `IO` value. The
 * result is either succeeded with a value `A`, or failed with a `Cause[E]`.
 */
sealed trait Exit[+E, +A] extends Product with Serializable { self =>
  import Exit.{ Cause => _, _ }

  /**
   * Parallelly zips the this result with the specified result discarding the first element of the tuple or else returns the failed `Cause[E1]`
   */
  final def &>[E1 >: E, B](that: Exit[E1, B]): Exit[E1, B] = zipWith(that)((_, _), _ && _).map(_._2)

  /**
   * Sequentially zips the this result with the specified result discarding the first element of the tuple or else returns the failed `Cause[E1]`
   */
  final def *>[E1 >: E, B](that: Exit[E1, B]): Exit[E1, B] = zipWith(that)((_, _), _ ++ _).map(_._2)

  /**
   * Parallelly zips the this result with the specified result discarding the second element of the tuple or else returns the failed `Cause[E1]`
   */
  final def <&[E1 >: E, B](that: Exit[E1, B]): Exit[E1, A] = zipWith(that)((_, _), _ && _).map(_._1)

  /**
   * Parallelly zips the this result with the specified result or else returns the failed `Cause[E1]`
   */
  final def <&>[E1 >: E, B](that: Exit[E1, B]): Exit[E1, (A, B)] = zipWith(that)((_, _), _ && _)

  /**
   * Sequentially zips the this result with the specified result discarding the second element of the tuple or else returns the failed `Cause[E1]`
   */
  final def <*[E1 >: E, B](that: Exit[E1, B]): Exit[E1, A] = zipWith(that)((_, _), _ ++ _).map(_._1)

  /**
   * Sequentially zips the this result with the specified result or else returns the failed `Cause[E1]`
   */
  final def <*>[E1 >: E, B](that: Exit[E1, B]): Exit[E1, (A, B)] = zipWith(that)((_, _), _ ++ _)

  /**
   * Replaces the success value with the one provided.
   */
  final def as[B](b: B): Exit[E, B] = map(_ => b)

  /**
   * Replaces the error value with the one provided.
   */
  final def asError[E1](e1: E1): Exit[E1, A] = mapError(_ => e1)

  /**
   * Maps over both the error and value type.
   */
  final def bimap[E1, A1](f: E => E1, g: A => A1): Exit[E1, A1] = mapError(f).map(g)

  @deprecated("use as", "1.0.0")
  final def const[B](b: B): Exit[E, B] = as(b)

  /**
   * Flat maps over the value type.
   */
  final def flatMap[E1 >: E, A1](f: A => Exit[E1, A1]): Exit[E1, A1] =
    self match {
      case Success(a)     => f(a)
      case e @ Failure(_) => e
    }

  /**
   * Flat maps over the value type.
   */
  final def flatMapM[E1 >: E, R, E2, A1](f: A => ZIO[R, E2, Exit[E1, A1]]): ZIO[R, E2, Exit[E1, A1]] =
    self match {
      case Success(a)     => f(a)
      case e @ Failure(_) => ZIO.succeed(e)
    }

  /**
   * Folds over the value or cause.
   */
  final def fold[Z](failed: Cause[E] => Z, completed: A => Z): Z =
    self match {
      case Success(v)     => completed(v)
      case Failure(cause) => failed(cause)
    }

  /**
   * Sequentially zips the this result with the specified result or else returns the failed `Cause[E1]`
   */
  final def foldM[R, E1, B](failed: Cause[E] => ZIO[R, E1, B], completed: A => ZIO[R, E1, B]): ZIO[R, E1, B] =
    self match {
      case Failure(cause) => failed(cause)
      case Success(v)     => completed(v)
    }

  /**
   * Retrieves the `A` if succeeded, or else returns the specified default `A`.
   */
  final def getOrElse[A1 >: A](orElse: Cause[E] => A1): A1 = self match {
    case Success(value) => value
    case Failure(cause) => orElse(cause)
  }

  /**
   * Determines if the result is interrupted.
   */
  final def interrupted: Boolean = self match {
    case Success(_) => false
    case Failure(c) => c.interrupted
  }

  /**
   * Maps over the value type.
   */
  final def map[A1](f: A => A1): Exit[E, A1] =
    self match {
      case Success(v)     => Exit.succeed(f(v))
      case e @ Failure(_) => e
    }

  /**
   * Maps over the error type.
   */
  final def mapError[E1](f: E => E1): Exit[E1, A] =
    self match {
      case e @ Success(_) => e
      case Failure(c)     => halt(c.map(f))
    }

  /**
   * Determines if the result is a success.
   */
  final def succeeded: Boolean = self match {
    case Success(_) => true
    case _          => false
  }

  /**
   * Converts the `Exit` to an `Either[Throwable, A]`, by wrapping the
   * cause in `FiberFailure` (if the result is failed).
   */
  final def toEither: Either[Throwable, A] = self match {
    case Success(value) => Right(value)
    case Failure(cause) => Left(FiberFailure(cause))
  }

  /**
   * Discards the value.
   */
  final def unit: Exit[E, Unit] = as(())

  /**
   * Named alias for `<*>`.
   */
  final def zip[E1 >: E, B](that: Exit[E1, B]): Exit[E1, (A, B)] = self <*> that

  /**
   * Named alias for `<*`.
   */
  final def zipLeft[E1 >: E, B](that: Exit[E1, B]): Exit[E1, A] = self <* that

  /**
   * Named alias for `<&>`.
   */
  final def zipPar[E1 >: E, B](that: Exit[E1, B]): Exit[E1, (A, B)] = self <&> that

  /**
   * Named alias for `<&`.
   */
  final def zipParLeft[E1 >: E, B](that: Exit[E1, B]): Exit[E1, A] = self <& that

  /**
   * Named alias for `&>`.
   */
  final def zipParRight[E1 >: E, B](that: Exit[E1, B]): Exit[E1, B] = self &> that

  /**
   * Named alias for `*>`.
   */
  final def zipRight[E1 >: E, B](that: Exit[E1, B]): Exit[E1, B] = self *> that

  /**
   * Zips this together with the specified result using the combination functions.
   */
  final def zipWith[E1 >: E, B, C](that: Exit[E1, B])(
    f: (A, B) => C,
    g: (Cause[E], Cause[E1]) => Cause[E1]
  ): Exit[E1, C] =
    (self, that) match {
      case (Success(a), Success(b)) => Exit.succeed(f(a, b))
      case (Failure(l), Failure(r)) => Exit.halt(g(l, r))
      case (e @ Failure(_), _)      => e
      case (_, e @ Failure(_))      => e
    }
}

object Exit extends Serializable {

  final case class Success[A](value: A)            extends Exit[Nothing, A]
  final case class Failure[E](cause: zio.Cause[E]) extends Exit[E, Nothing]

  final def interrupt(id: FiberId): Exit[Nothing, Nothing] = halt(zio.Cause.interrupt(id))

  final def collectAll[E, A](exits: Iterable[Exit[E, A]]): Option[Exit[E, List[A]]] =
    exits.headOption.map { head =>
      exits
        .drop(1)
        .foldLeft(head.map(List(_)))((acc, el) => acc.zipWith(el)((acc, el) => el :: acc, _ ++ _))
        .map(_.reverse)
    }

  /**
   *  Alias for [[Exit.collectAll]]
   */
  final def sequence[E, A](exits: Iterable[Exit[E, A]]): Option[Exit[E, List[A]]] =
    collectAll[E, A](exits)

  final def collectAllPar[E, A](exits: Iterable[Exit[E, A]]): Option[Exit[E, List[A]]] =
    exits.headOption.map { head =>
      exits
        .drop(1)
        .foldLeft(head.map(List(_)))((acc, el) => acc.zipWith(el)((acc, el) => el :: acc, _ && _))
        .map(_.reverse)
    }

  /**
   *  Alias for [[Exit.collectAllPar]]
   */
  final def sequencePar[E, A](exits: Iterable[Exit[E, A]]): Option[Exit[E, List[A]]] =
    collectAllPar[E, A](exits)

  final def die(t: Throwable): Exit[Nothing, Nothing] = halt(zio.Cause.die(t))

  final def fail[E](error: E): Exit[E, Nothing] = halt(zio.Cause.fail(error))

  final def flatten[E, A](exit: Exit[E, Exit[E, A]]): Exit[E, A] =
    exit.flatMap(identity)

  final def fromEither[E, A](e: Either[E, A]): Exit[E, A] =
    e.fold(fail, succeed)

  final def fromOption[A](o: Option[A]): Exit[Unit, A] =
    o.fold[Exit[Unit, A]](fail(()))(succeed)

  final def fromTry[A](t: scala.util.Try[A]): Exit[Throwable, A] =
    t match {
      case scala.util.Success(a) => succeed(a)
      case scala.util.Failure(t) => fail(t)
    }

  final def halt[E](cause: zio.Cause[E]): Exit[E, Nothing] = Failure(cause)

  final def succeed[A](a: A): Exit[Nothing, A] = Success(a)

  final def unit: Exit[Nothing, Unit] = succeed(())

  @deprecated("use zio.Cause", "1.0.0")
  type Cause[+E] = zio.Cause[E]

  @deprecated("use zio.Cause", "1.0.0")
  val Cause = zio.Cause

}
