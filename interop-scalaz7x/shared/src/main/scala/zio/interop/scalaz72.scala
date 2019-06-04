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

package zio.interop

import scalaz.Tags.Parallel
import scalaz.{ @@, \/, Applicative, Bifunctor, BindRec, Monad, MonadError, MonadPlus, Monoid, Plus, Tag }
import zio.{ Task, TaskR, ZIO }

object scalaz72 extends ZIOInstances with Scalaz72Platform {
  type ParIO[R, E, A] = ZIO[R, E, A] @@ Parallel
}

abstract class ZIOInstances extends ZIOInstances1 {

  implicit def zioTaskInstances[R]
    : MonadError[TaskR[R, ?], Throwable] with BindRec[TaskR[R, ?]] with Plus[TaskR[R, ?]] =
    new ZIOMonadError[R, Throwable] with ZIOPlus[R, Throwable]

  // cached for efficiency
  implicit val taskInstances: MonadError[Task, Throwable] with BindRec[Task] with Plus[Task] =
    new ZIOMonadError[Any, Throwable] with ZIOPlus[Any, Throwable]

  implicit val taskParAp: Applicative[scalaz72.ParIO[Any, Throwable, ?]] = new ZIOParApplicative[Any, Throwable]
}

sealed trait ZIOInstances1 extends ZIOInstances2 {
  implicit def zioMonoidInstances[R, E: Monoid]
    : MonadError[ZIO[R, E, ?], E] with BindRec[ZIO[R, E, ?]] with Bifunctor[ZIO[R, ?, ?]] with MonadPlus[ZIO[R, E, ?]] =
    new ZIOMonadPlus[R, E] with ZIOBifunctor[R]

  implicit def zioParAp[R, E]: Applicative[scalaz72.ParIO[R, E, ?]] = new ZIOParApplicative[R, E]
}

sealed trait ZIOInstances2 {
  implicit def zioInstances[R, E]
    : MonadError[ZIO[R, E, ?], E] with BindRec[ZIO[R, E, ?]] with Bifunctor[ZIO[R, ?, ?]] with Plus[ZIO[R, E, ?]] =
    new ZIOMonadError[R, E] with ZIOPlus[R, E] with ZIOBifunctor[R]
}

private class ZIOMonad[R, E] extends Monad[ZIO[R, E, ?]] with BindRec[ZIO[R, E, ?]] {
  override def map[A, B](fa: ZIO[R, E, A])(f: A => B): ZIO[R, E, B]             = fa.map(f)
  override def point[A](a: => A): ZIO[R, E, A]                                  = ZIO.succeedLazy(a)
  override def bind[A, B](fa: ZIO[R, E, A])(f: A => ZIO[R, E, B]): ZIO[R, E, B] = fa.flatMap(f)
  override def tailrecM[A, B](f: A => ZIO[R, E, A \/ B])(a: A): ZIO[R, E, B] =
    f(a).flatMap(_.fold(tailrecM(f), point(_)))
}

private class ZIOMonadError[R, E] extends ZIOMonad[R, E] with MonadError[ZIO[R, E, ?], E] {
  override def handleError[A](fa: ZIO[R, E, A])(f: E => ZIO[R, E, A]): ZIO[R, E, A] = fa.catchAll(f)
  override def raiseError[A](e: E): ZIO[R, E, A]                                    = ZIO.fail(e)
}

/** lossy, throws away errors using the "first success" interpretation of Plus */
private trait ZIOPlus[R, E] extends Plus[ZIO[R, E, ?]] {
  override def plus[A](a: ZIO[R, E, A], b: => ZIO[R, E, A]): ZIO[R, E, A] = a.orElse(b)
}

private class ZIOMonadPlus[R, E: Monoid] extends ZIOMonadError[R, E] with MonadPlus[ZIO[R, E, ?]] {
  override def plus[A](a: ZIO[R, E, A], b: => ZIO[R, E, A]): ZIO[R, E, A] =
    a.catchAll { e1 =>
      b.catchAll { e2 =>
        ZIO.fail(Monoid[E].append(e1, e2))
      }
    }
  override def empty[A]: ZIO[R, E, A] = raiseError(Monoid[E].zero)
}

private trait ZIOBifunctor[R] extends Bifunctor[ZIO[R, ?, ?]] {
  override def bimap[A, B, C, D](fab: ZIO[R, A, B])(f: A => C, g: B => D): ZIO[R, C, D] =
    fab.bimap(f, g)
}

private class ZIOParApplicative[R, E] extends Applicative[scalaz72.ParIO[R, E, ?]] {
  override def point[A](a: => A): scalaz72.ParIO[R, E, A] = Tag(ZIO.succeedLazy(a))
  override def ap[A, B](fa: => scalaz72.ParIO[R, E, A])(f: => scalaz72.ParIO[R, E, A => B]): scalaz72.ParIO[R, E, B] = {
    lazy val fa0: ZIO[R, E, A] = Tag.unwrap(fa)
    Tag(Tag.unwrap(f).flatMap(x => fa0.map(x)))
  }

  override def map[A, B](fa: scalaz72.ParIO[R, E, A])(f: A => B): scalaz72.ParIO[R, E, B] =
    Tag(Tag.unwrap(fa).map(f))

  override def apply2[A, B, C](
    fa: => scalaz72.ParIO[R, E, A],
    fb: => scalaz72.ParIO[R, E, B]
  )(f: (A, B) => C): scalaz72.ParIO[R, E, C] =
    Tag(Tag.unwrap(fa).zipPar(Tag.unwrap(fb)).map(f.tupled))
}
