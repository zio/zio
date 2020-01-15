package zio.stream

import zio._
import zio.clock.Clock
import zio.duration.Duration

abstract class ZSinkNew[-R, +E, -A, +B] extends Serializable { self =>

  type S

  def initial: ZIO[R, E, S]

  def step(s: S, a: A): ZIO[R, Option[E], S]

  def extract(s: S): ZIO[R, E, B]

  final def <&>[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, (B, C)] =
    zipPar(that)

  final def <*>[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, (B, C)] =
    zip(that)

  final def <&[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, B] =
    zipParLeft(that)

  final def <*[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, B] =
    zipLeft(that)

  final def &>[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, C] =
    zipParRight(that)

  final def *>[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, C] =
    zipRight(that)

  final def <|[R1 <: R, E1 >: E, A1 <: A, B1 >: B](that: ZSinkNew[R1, E1, A1, B1]): ZSinkNew[R1, E1, A1, (B1, Chunk[A1])] =
    orElse(that).map(_.fold((_, Chunk.empty), identity))

  final def |[R1 <: R, E1 >: E, A1 <: A, B1 >: B](that: ZSinkNew[R1, E1, A1, B1]): ZSinkNew[R1, E1, A1, B1] =
    race(that)

  def ? : ZSinkNew[R, Nothing, A, Option[B]] =
    new ZSinkNew[R, Nothing, A, Option[B]] {
      type S = Option[self.S]

      val initial = self.initial.option

      def step(s: S, a: A) = s match {
        case Some(s) => self.step(s, a).option
        case None => IO.fail(None)
      }

      def extract(s: S) = s match {
        case Some(s) => self.extract(s).option
        case None => UIO.succeed(None)
      }
    }

  final def absolve[E1, B1](implicit ev1: E <:< Nothing, ev2: B <:< Either[E1, B1]): ZSinkNew[R, E1, A, B1] = {
    val _ = (ev1, ev2)
    self.asInstanceOf[ZSinkNew[R, Nothing, A, Either[E1, B1]]].flatMap {
      case Left(e) => ZSinkNew.fail(e)
      case Right(b) => ZSinkNew.succeed(b)
    }
  }

  /**
    * Used to emulate old ZSink leftover behavior. Useful for implementing
    * `orElse`.
    */
  final def accum[A1 <: A]: ZSinkNew[R, E, A1, (B, Chunk[A1])] =
    zip(ZSinkNew.accum[A1])

  final def as[C](c: => C): ZSinkNew[R, E, A, C] =
    map(_ => c)

  final def asError[E1](e1: => E1): ZSinkNew[R, E1, A, B] =
    mapError(_ => e1)

  def contramap[C](f: C => A): ZSinkNew[R, E, C, B] =
    new ZSinkNew[R, E, C, B] {
      type S = self.S
      val initial = self.initial
      def step(s: S, c: C) = self.step(s, f(c))
      def extract(s: S) = self.extract(s)
    }

  final def contramapM[R1 <: R, E1 >: E, C](f: C => ZIO[R1, E1, A]): ZSinkNew[R1, E1, C, B] =
    new ZSinkNew[R1, E1, C, B] {
      type S = self.S
      val initial = self.initial
      def step(s: S, c: C) = f(c).mapError(Some(_)).flatMap(self.step(s, _))
      def extract(s: S) = self.extract(s)
    }

  def dimap[C, D](f: C => A)(g: B => D): ZSinkNew[R, E, C, D] =
    self.contramap(f).map(g)

  def either: ZSinkNew[R, Nothing, A, Either[E, B]] =
    new ZSinkNew[R, Nothing, A, Either[E, B]] {
      type S = Either[E, self.S]

      val initial = self.initial.either

      def step(s: S, a: A) = s match {
        case Left(_) =>
          IO.fail(None)
        case Right(s) =>
          self.step(s, a).foldM({
            case Some(e) => UIO.succeed(Left(e))
            case None => IO.fail(None)
          }, x => UIO.succeed(Right(x)))
      }

      def extract(s: S) = s match {
        case Left(e) => UIO.succeed(Left(e))
        case Right(s) => self.extract(s).either
      }
    }

  def filter[A1 <: A](p: A1 => Boolean): ZSinkNew[R, E, A1, B] =
    new ZSinkNew[R, E, A1, B] {
      type S = self.S
      val initial = self.initial
      def step(s: S, a: A1) = if (p(a)) self.step(s, a) else UIO.succeed(s)
      def extract(s: S) = self.extract(s)
    }

  final def flatMap[R1 <: R, E1 >: E, A1 <: A, C](f: B => ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, C] =
    new ZSinkNew[R1, E1, A1, C] {
      type S = Either[self.S, (ZSinkNew[R1, E1, A1, C], Any)]

      val initial = self.initial.map(Left(_))

      def step(s: S, a: A1) = s match {
        case Left(s) =>
          self.step(s, a).foldCauseM(
            Cause
              .sequenceCauseOption(_)
              .fold(switch(s, a).map(Right(_)))(c => IO.halt(c).mapError(Some(_))),
            s => UIO.succeed(Left(s))
          )

        case Right((that, s)) =>
          that.step(s.asInstanceOf[that.S], a).map(s => Right((that, s)))
      }

      def extract(s: S) = s match {
        case Left(s) =>
          for {
            b <- self.extract(s)
            that = f(b)
            s <- that.initial
            c <- that.extract(s)
          } yield c

        case Right((that, s)) =>
          that.extract(s.asInstanceOf[that.S])
      }

      def switch(s: self.S, a: A1): ZIO[R1, Option[E1], (ZSinkNew[R1, E1, A1, C], Any)] =
        self.extract(s).mapError(Some(_)).flatMap { b =>
          val that = f(b)
          that.initial.mapError(Some(_)).flatMap(that.step(_, a)).map((that, _))
        }
    }

   def map[C](f: B => C): ZSinkNew[R, E, A, C] =
    new ZSinkNew[R, E, A, C] {
      type S = self.S
      val initial = self.initial
      def step(s: S, a: A) = self.step(s, a)
      def extract(s: S) = self.extract(s).map(f)
    }

  def mapError[E1](f: E => E1): ZSinkNew[R, E1, A, B] =
    new ZSinkNew[R, E1, A, B] {
      type S = self.S
      val initial = self.initial.mapError(f)
      def step(s: S, a: A) = self.step(s, a).mapError(_.map(f))
      def extract(s: S) = self.extract(s).mapError(f)
    }

  final def mapM[R1 <: R, E1 >: E, C](f: B => ZIO[R1, E1, C]): ZSinkNew[R1, E1, A, C] =
    self.flatMap(b => ZSinkNew.fromEffect(f(b)))

  final def orElse[R1 <: R, E1, A1 <: A, C](
    that: ZSinkNew[R1, E1, A1, C]
  ): ZSinkNew[R1, E1, A1, Either[B, (C, Chunk[A1])]] =
    self.?.zipPar(that.either.accum[A1]).flatMap {
      case (Some(b), _) => ZSinkNew.succeed(Left(b))
      case (None, (Left(e), _)) => ZSinkNew.fail(e)
      case (None, (Right(c), leftovers)) => ZSinkNew.succeed(Right((c, leftovers)))
    }

  final def provide(r: R)(implicit ev: NeedsEnv[R]): ZSinkNew[Any, E, A, B] =
    new ZSinkNew[Any, E, A, B] {
      type S = self.S
      val initial = self.initial.provide(r)
      def step(s: S, a: A) = self.step(s, a).provide(r)
      def extract(s: S) = self.extract(s).provide(r)
    }

  final def provideSome[R1](f: R1 => R)(implicit ev: NeedsEnv[R]): ZSinkNew[R1, E, A, B] =
    new ZSinkNew[R1, E, A, B] {
      type S = self.S
      val initial = self.initial.provideSome(f)
      def step(s: S, a: A) = self.step(s, a).provideSome(f)
      def extract(s: S) = self.extract(s).provideSome(f)
    }

  final def race[R1 <: R, E1 >: E, A1 <: A, B1 >: B](that:ZSinkNew[R1, E1, A1, B1]): ZSinkNew[R1, E1, A1, B1] =
    raceBoth(that).map(_.merge)

  final def raceBoth[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, Either[B, C]] =
    self.either.zipPar(that.either).flatMap {
      case (Left(e), Left(e1)) =>
        ZSinkNew.halt(Cause.Both(Cause.Fail(e), Cause.Fail(e1)))
      case (Left(_), Right(c)) =>
        ZSinkNew.succeed(Right(c))
      case (Right(b), _) =>
        ZSinkNew.succeed(Left(b))
    }

  final def tapInput[R1 <: R, E1 >: E, A1 <: A](f: A1 => ZIO[R1, E1, Unit]): ZSinkNew[R1, E1, A1, B] =
    contramapM(a => f(a).as(a))

  final def tapOutput[R1 <: R, E1 >: E, A1 <: A](f: B => ZIO[R1, E1, Unit]): ZSinkNew[R1, E1, A1, B] =
    mapM(b => f(b).as(b))

  final def timed: ZSinkNew[R with Clock, E, A, (Duration, B)] =
    new ZSinkNew[R with Clock, E, A, (Duration, B)] {
      type S = (Long, Long, self.S)

      val initial = for {
        s <- self.initial
        t <- clock.nanoTime
      } yield (t, 0L, s)

      def step(s: S, a: A) = s match {
        case (t, total, s) =>
          for {
            s <- self.step(s, a)
            now <- clock.nanoTime
            t1 = now - t
          } yield (now, total + t1, s)
      }

      def extract(s: S) = self.extract(s._3).map((Duration.fromNanos(s._2), _))
    }

  final def unit: ZSinkNew[R, E, A, Unit] =
    as(())

  def update(s: self.S): ZSinkNew[R, E, A, B] =
    new ZSinkNew[R, E, A, B] {
      type S = self.S
      val initial = UIO.succeed(s)
      def step(s: S, a: A) = self.step(s, a)
      def extract(s: S) = self.extract(s)
    }

  final def zip[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, (B, C)] =
    flatMap(b => that.map((b, _)))

  final def zipLeft[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, B] =
    zip(that).map(_._1)

  final def zipRight[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, C] =
    zip(that).map(_._2)

  final def zipPar[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, (B, C)] =
    zipWithPar(that)((_, _))

  final def zipParLeft[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, B] =
    zipWithPar(that)((b, _) => b)

  final def zipParRight[R1 <: R, E1 >: E, A1 <: A, C](that: ZSinkNew[R1, E1, A1, C]): ZSinkNew[R1, E1, A1, C] =
    zipWithPar(that)((_, c) => c)

  final def zipWith[R1 <: R, E1 >: E, A1 <: A, C, D](
    that: ZSinkNew[R1, E1, A1, C]
  )(f: (B, C) => D): ZSinkNew[R1, E1, A1, D] =
    zip(that).map(f.tupled)

  final def zipWithPar[R1 <: R, E1 >: E, A1 <: A, C, D](
    that: ZSinkNew[R1, E1, A1, C])(f: (B, C) => D
  ): ZSinkNew[R1, E1, A1, D] =
    new ZSinkNew[R1, E1, A1, D] {
      type S = (self.S, that.S)
      val initial = self.initial.zipPar(that.initial)
      def step(s: S, a: A1) = self.step(s._1, a).zipPar(that.step(s._2, a))
      def extract(s: S) = self.extract(s._1).zipPar(that.extract(s._2)).map(f.tupled)
    }
}

object ZSinkNew extends Serializable {

  def accum[A]: ZSinkNew[Any, Nothing, A, Chunk[A]] =
    new ZSinkNew[Any, Nothing, A, Chunk[A]] {
      type S = Chunk[A]
      val initial = UIO.succeed(Chunk.empty)
      def step(s: S, a: A) = UIO.succeed(s + a)
      def extract(s: S) = UIO.succeed(s)
    }

  def fail[E](e: E): ZSinkNew[Any, E, Any, Nothing] =
    new ZSinkNew[Any, E, Any, Nothing] {
      type S = Unit
      val initial = UIO.succeed(())
      def step(s: S, a: Any) = IO.fail(None)
      def extract(s: S) = IO.fail(e)
    }

  def fromEffect[R, E, B](b: => ZIO[R, E, B]): ZSinkNew[R, E, Any, B] =
    new ZSinkNew[R, E, Any, B] {
      type S = Unit
      val initial = UIO.succeed(())
      def step(s: S, a: Any) = IO.fail(None)
      def extract(s: S) = b
    }

  def halt[E](cause: Cause[E]): ZSinkNew[Any, E, Any, Nothing] =
    new ZSinkNew[Any, E, Any, Nothing] {
      type S = Unit
      val initial = UIO.succeed(())
      def step(s: S, a: Any) = IO.fail(None)
      def extract(s: S) = IO.halt(cause)
    }

  def succeed[B](b: B): ZSinkNew[Any, Nothing, Any, B] =
    new ZSinkNew[Any, Nothing, Any, B] {
      type S = Unit
      val initial = UIO.succeed(())
      def step(s: S, a: Any) = IO.fail(None)
      def extract(s: S) = UIO.succeed(b)
    }
}
