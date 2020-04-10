package zio.stream.experimental

import zio._

abstract class ZTransducer[-R, +E, -I, +O](
  val push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Option[E], Chunk[O]]]
) extends ZConduit[R, E, I, O, Unit](push.map(_.andThen(_.mapError {
      case Some(e) => Left(e)
      case None    => Right(())
    }))) { self =>

  /**
   * Compose this transducer with another transducer, resulting in a composite transducer.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, O3](that: ZTransducer[R1, E1, O2, O3]): ZTransducer[R1, E1, I, O3] =
    ZTransducer {
      for {
        pushLeft  <- self.push
        pushRight <- that.push
        push = (input: Option[Chunk[I]]) =>
          pushLeft(input).foldM(
            {
              case Some(e) => ZIO.fail(Some(e))
              case None    => pushRight(None)
            },
            chunk => pushRight(Some(chunk))
          )
      } yield push
    }

  /**
   * Compose this transducer with a sink, resulting in a sink that processes elements by piping
   * them through this transducer and piping the results into the sink.
   */
  def >>>[R1 <: R, E1 >: E, O2 >: O, I1 <: I, Z](that: ZSink[R1, E1, O2, Z]): ZSink[R1, E1, I1, Z] =
    ZSink {
      for {
        pushSelf <- self.push
        pushThat <- that.push
        push = (is: Option[Chunk[I]]) =>
          pushSelf(is).foldM(
            {
              case Some(e) => ZIO.fail(Left(e))
              case None    => pushThat(None)
            },
            chunk => pushThat(Some(chunk))
          )
      } yield push
    }

  final def map[P](f: O => P): ZTransducer[R, E, I, P] =
    ZTransducer(self.push.map(push => i => push(i).map(_.map(f))))
}

object ZTransducer {
  def apply[R, E, I, O](
    push: ZManaged[R, Nothing, Option[Chunk[I]] => ZIO[R, Option[E], Chunk[O]]]
  ): ZTransducer[R, E, I, O] =
    new ZTransducer(push) {}

  /**
   * A transducer that re-chunks the elements fed to it into chunks of up to
   * `n` elements each.
   */
  def chunkN[I](n: Int): ZTransducer[Any, Nothing, I, I] =
    ZTransducer {
      for {
        buffered <- ZRef.makeManaged[Chunk[I]](Chunk.empty)
        push = { (input: Option[Chunk[I]]) =>
          input match {
            case None =>
              buffered.modify(buf => (if (buf.isEmpty) Push.done else Push.emit(buf)) -> Chunk.empty).flatten
            case Some(is) =>
              buffered.modify { buf0 =>
                val buf = buf0 ++ is
                if (buf.length >= n) {
                  val (out, buf1) = buf.splitAt(n)
                  Push.emit(out) -> buf1
                } else
                  Push.next -> buf
              }.flatten
          }
        }
      } yield push
    }

  def collectAllWhile[I](p: I => Boolean): ZTransducer[Any, Nothing, I, List[I]] =
    ZTransducer {
      for {
        buffered <- ZRef.makeManaged[(Chunk[I], Chunk[I])](Chunk.empty -> Chunk.empty)
        push = { (in: Option[Chunk[I]]) =>
          buffered.modify { case (buf0, out0) =>
            if (buf0.isEmpty && in.isEmpty)
              (if (out0.isEmpty) Push.done else Push.emit(out0.toList)) -> (Chunk.empty -> Chunk.empty)
            else {
              val buf = in.foldLeft(buf0)(_ ++ _)
              val out = buf.takeWhile(p)
              if (out.nonEmpty && out.length < buf.length)
                Push.emit((out0 ++ out).toList) -> (buf.drop(out.length).dropWhile(!p(_)) -> Chunk.empty)
              else
                Push.next -> (buf.drop(out.length).dropWhile(!p(_)) -> (out0 ++ out)) }
          }.flatten
        }
      } yield push
    }

  def fail[E](e: => E): ZTransducer[Any, E, Any, Nothing] =
    ZTransducer(ZManaged.succeed((_: Option[Any]) => Push.fail(e)))

  def fromPush[R, E, I, O](push: Option[Chunk[I]] => ZIO[R, Option[E], Chunk[O]]): ZTransducer[R, E, I, O] =
    ZTransducer(Managed.succeed(push))

  object Push {
    def emit[A](a: A): UIO[Chunk[A]]                 = IO.succeedNow(Chunk.single(a))
    def emit[A](as: Chunk[A]): UIO[Chunk[A]]         = IO.succeedNow(as)
    def fail[E](e: E): IO[Option[E], Nothing]        = IO.fail(Some(e))
    def halt[E](c: Cause[E]): IO[Option[E], Nothing] = IO.halt(c).mapError(Some(_))
    val done: IO[Option[Nothing], Nothing]           = IO.fail(None)
    val next: UIO[Chunk[Nothing]]                    = IO.succeedNow(Chunk.empty)
  }
}
