package zio.stream.experimental

import zio._

abstract class ZSink[-R, +E, -I, +Z] private (
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]]
) extends ZConduit[R, E, I, Unit, Z](push.map(push => input => push(input).as(Chunk.empty))) { self =>
  import ZSink.Push

  /**
   * Transforms this sink's result.
   */
  def map[Z2](f: Z => Z2): ZSink[R, E, I, Z2] =
    ZSink[R, E, I, Z2](self.push.map(push => input => push(input).mapError(_.right.map(f))))

  /**
   * Transforms this sink's input elements.
   */
  def contramap[I2](f: I2 => I): ZSink[R, E, I2, Z] =
    contramapChunks(_.map(f))

  /**
   * Effectfully transforms this sink's input elements.
   */
  def contramapM[R1 <: R, E1 >: E, I2](f: I2 => ZIO[R1, E1, I]): ZSink[R1, E1, I2, Z] =
    contramapChunksM(_.mapM(f))

  /**
   * Transforms this sink's input chunks.
   */
  def contramapChunks[I2](f: Chunk[I2] => Chunk[I]): ZSink[R, E, I2, Z] =
    ZSink(self.push.map(push => input => push(input.map(f))))

  /**
   * Effectfully transforms this sink's input chunks.
   */
  def contramapChunksM[R1 <: R, E1 >: E, I2](f: Chunk[I2] => ZIO[R1, E1, Chunk[I]]): ZSink[R1, E1, I2, Z] =
    ZSink(
      self.push.map(push =>
        input =>
          input match {
            case Some(value) => f(value).mapError(Left(_)).flatMap(is => push(Some(is)))
            case None        => push(None)
          }
      )
    )

  /**
   * Repeatedly runs the sink on the incoming elements for as long as they satisfy
   * the predicate `p`. The sink's results will be accumulated using the stepping
   * function `f`.
   */
  def collectAllWhileWith[I1 <: I, S](z: S)(p: I1 => Boolean)(f: (S, Z) => S): ZSink[R, E, I1, S] =
    ZSink {
      for {
        pushAndRestart  <- Push.restartablePush(self.push)
        state           <- Ref.make(z).toManaged_
        (push, restart) = pushAndRestart
        foldingPush = (input: Option[Chunk[I1]]) => {
          val (step, done) = input match {
            case None => push(None) -> true
            case Some(is) =>
              val filtered = is.takeWhile(p)

              if (filtered.size < is.size) push(Some(filtered)) -> true
              else push(Some(filtered))                         -> false
          }

          step.foldM(
            {
              case Left(e)  => ZIO.fail(Left(e))
              case Right(z) => state.update(f(_, z))
            },
            _ => UIO.unit
          ) *> (if (done) state.get.flatMap(s => ZIO.fail(Right(s)))
                else UIO.unit)
        }

      } yield foldingPush
    }

  /**
   * Converts this sink to a transducer that feeds incoming elements to the sink
   * and emits the sink's results as outputs. The sink will be restarted when
   * it ends.
   */
  def toTransducer: ZTransducer[R, E, I, Z] =
    ZTransducer {
      Push.restartablePush(self.push).map {
        case (push, restart) =>
          (input: Option[Chunk[I]]) =>
            push(input)
              .foldM(
                {
                  case Left(e)  => ZIO.fail(Left(e))
                  case Right(z) => restart.as(Chunk.single(z))
                },
                _ => UIO.succeed(Chunk.empty)
              )
      }
    }

  /**
   * Creates a sink that produces values until one verifies
   * the predicate `f`.
   */
  def untilOutputM[R1 <: R, E1 >: E](f: Z => ZIO[R1, E1, Boolean]) =
    ZSink {
      Push.restartablePush(self.push).map {
        case (pushUpstream, restartUpstream) =>
          (input: Option[Chunk[I]]) =>
            pushUpstream(input).catchAll {
              case e @ Left(_) => ZIO.fail(e)
              case Right(z) =>
                f(z).mapError(Left(_)) flatMap {
                  if (_) ZIO.fail(Right(z))
                  else restartUpstream
                }
            }
      }
    }
}

object ZSink {
  type Push[-R, +E, -I, +Z] = Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]

  object Push {
    def emit[Z](z: Z): IO[Either[Nothing, Z], Nothing]        = IO.fail(Right(z))
    def fail[E](e: E): IO[Either[E, Nothing], Nothing]        = IO.fail(Left(e))
    def halt[E](c: Cause[E]): IO[Either[E, Nothing], Nothing] = IO.halt(c).mapError(Left(_))
    val next: UIO[Unit]                                       = IO.unit

    /**
     * Decorates a push with a ZIO value that re-initializes it with a fresh state.
     */
    def restartablePush[R, E, I, Z](
      push: ZManaged[R, Nothing, Push[R, E, I, Z]]
    ): ZManaged[R, Nothing, (Push[R, E, I, Z], ZIO[R, Nothing, Unit])] =
      for {
        switchSink  <- ZManaged.switchable[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]]
        initialPush <- switchSink(push).toManaged_
        currPush    <- Ref.make(initialPush).toManaged_
        restart     = switchSink(push).flatMap(currPush.set)
        newPush     = (input: Option[Chunk[I]]) => currPush.get.flatMap(_.apply(input))
      } yield (newPush, restart)

    /**
     * Wraps the push with a mechanism that keeps track of leftovers. When the push
     * yields a result, the leftovers will be concatenated to an internal variable.
     * The next chunk pushed will have those leftovers prepended.
     */
    def withLeftovers[R, E, I, Z](
      push: Push[R, E, I, Remainder[I, Z]]
    ): ZManaged[R, Nothing, Push[R, E, I, Remainder[I, Z]]] =
      for {
        leftoversRef <- Ref.make[Chunk[I]](Chunk.empty).toManaged_
        logLeftovers = (leftovers: Chunk[I]) => leftoversRef.update(_ ++ leftovers)
        pushWithLeftovers = (input: Option[Chunk[I]]) =>
          leftoversRef
            .getAndSet(Chunk.empty)
            .flatMap { leftovers =>
              input match {
                case Some(is) => push(Some(leftovers ++ is))
                case None =>
                  if (leftovers.nonEmpty) push(Some(leftovers)) *> push(None)
                  else push(None)
              }
            }
            .catchAll {
              case l @ Left(_) => ZIO.fail(l)
              case r @ Right(Remainder(leftover, _)) =>
                logLeftovers(leftover) *> ZIO.fail(r)
            }
      } yield pushWithLeftovers
  }

  case class Remainder[I, Z](leftover: Chunk[I], result: Z)

  implicit class RemainderOps[R, E, I, Z](self: ZSink[R, E, I, Remainder[I, Z]]) {

    /**
     * Creates a sink that produces values until one verifies
     * the predicate `f`.
     */
    def untilOutputM[R1 <: R, E1 >: E](f: Z => ZIO[R1, E1, Boolean]): ZSink[R1, E1, I, Remainder[I, Z]] =
      ZSink {
        for {
          pushComponents <- Push.restartablePush(self.push).flatMap {
                             case (push, restart) =>
                               Push.withLeftovers(push).map((_, restart))
                           }
          (pushUpstream, restartUpstream) = pushComponents
          push = (input: Option[Chunk[I]]) =>
            pushUpstream(input).catchAll {
              case e @ Left(_) => ZIO.fail(e)
              case Right(z) =>
                f(z.result).mapError(Left(_)) flatMap {
                  if (_) ZIO.fail(Right(z))
                  else restartUpstream
                }
            }
        } yield push
      }

    /**
     * Repeatedly runs the sink on the incoming elements for as long as they satisfy
     * the predicate `p`. The sink's results will be accumulated using the stepping
     * function `f`.
     */
    def collectAllWhileWith[I1 <: I, S](z: S)(p: I1 => Boolean)(f: (S, Z) => S): ZSink[R, E, I1, S] =
      ZSink {
        for {
          pushAndRestart <- Push.restartablePush(self.push).flatMap {
                             case (push, restart) =>
                               Push.withLeftovers(push).map((_, restart))
                           }
          state           <- Ref.make(z).toManaged_
          (push, restart) = pushAndRestart
          foldingPush = (input: Option[Chunk[I1]]) => {
            val (step, done) = input match {
              case None => push(None) -> true
              case Some(is) =>
                val filtered = is.takeWhile(p)

                if (filtered.size < is.size) push(Some(filtered)) -> true
                else push(Some(filtered))                         -> false
            }

            step.foldM(
              {
                case Left(e)  => ZIO.fail(Left(e))
                case Right(z) => state.update(f(_, z.result))
              },
              _ => UIO.unit
            ) *> (if (done) state.get.flatMap(s => ZIO.fail(Right(s)))
                  else UIO.unit)
          }

        } yield foldingPush
      }
  }

  def apply[R, E, I, Z](push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]]) =
    new ZSink(push) {}

  def collectAll[A]: ZSink[Any, Nothing, A, List[A]] =
    ZSink {
      for {
        as <- ZRef.makeManaged[Chunk[A]](Chunk.empty)
        push = (xs: Option[Chunk[A]]) =>
          xs match {
            case Some(xs) => as.update(_ ++ xs) *> Push.next
            case None     => as.get.flatMap(as => Push.emit(as.toList))
          }
      } yield push
    }

  /**
   * A sink that counts the number of elements fed to it.
   */
  val count: ZSink[Any, Nothing, Any, Long] =
    fold(0L)((s, _) => s + 1)

  def fromPush[R, E, I, Z](push: Option[Chunk[I]] => ZIO[R, Either[E, Z], Unit]): ZSink[R, E, I, Z] =
    ZSink(Managed.succeed(push))

  /**
   * A sink that immediately ends with the specified value.
   */
  def succeed[Z](z: Z): ZSink[Any, Nothing, Any, Z] =
    fromPush {
      case Some(_) => UIO.unit
      case None    => ZIO.fail(Right(z))
    }

  /**
   * A sink that effectfully folds its inputs with the provided function and initial state.
   */
  def foldM[R, E, I, S](z: S)(f: (S, I) => ZIO[R, E, S]): ZSink[R, E, I, S] =
    ZSink {
      for {
        state <- Ref.make(z).toManaged_
        push = { (inputs: Option[Chunk[I]]) =>
          inputs match {
            case None => state.get.flatMap(s => ZIO.fail(Right(s)))
            case Some(value) =>
              state.get
                .flatMap(value.foldM(_)(f))
                .flatMap(state.set)
                .mapError(Left(_))
          }
        }
      } yield push
    }

  /**
   * A sink that folds its inputs with the provided function and initial state.
   */
  def fold[I, S](z: S)(f: (S, I) => S): ZSink[Any, Nothing, I, S] =
    ZSink {
      for {
        state <- Ref.make(z).toManaged_
        push = { (inputs: Option[Chunk[I]]) =>
          inputs match {
            case None => state.get.flatMap(s => ZIO.fail(Right(s)))
            case Some(value) =>
              state.update(value.fold(_)(f))
          }
        }
      } yield push
    }

  /**
   * Creates a single-value sink produced from an effect
   */
  def fromEffect[R, E, Z](b: => ZIO[R, E, Z]): ZSink[R, E, Any, Z] =
    ZSink(Managed.succeedNow {
      case None => b.foldM(Push.fail, Push.emit)
      case _    => b.foldM(Push.fail, _ => Push.next)
    })

}
