package zio.stream.experimental.internal

import zio.stream.experimental.ZSink
import zio.stream.experimental.ZSink.Push
import zio.{ UIO, URIO, ZIO, ZRef }

/**
 * Utility class that does common heavy lifting
 * for `ZSink.raceBoth` and `ZSink.zipWithPar`
 */
object ZSinkParallelHelper {
  case class Results[Z1, Z2, E](left: Option[Either[E, Z1]], right: Option[Either[E, Z2]])

  /**
   * Runs two sinks in parallel and keeps track of sink's states.
   * At each step tries to resolve the final value of type `Z`.
   */
  def runBoth[R, E, I, Z1, Z2, Z](one: ZSink[R, E, I, Z1], another: ZSink[R, E, I, Z2])(
    extract: Results[Z1, Z2, E] => ZIO[R, Either[E, Z], Unit]
  ): ZSink[R, E, I, Z] = {
    val done: UIO[Right[Nothing, Unit]] = ZIO.succeedNow(Right(()))

    ZSink(for {
      ref <- ZRef.makeManaged[Results[Z1, Z2, E]](Results(None, None))
      p1  <- one.push
      p2  <- another.push
      push: Push[R, E, I, Z] = {
        in =>
          ref.get.flatMap {
            state =>
              val newState: URIO[R, Results[Z1, Z2, E]] = {
                val leftUpdate  = if (state.left.isDefined) done else p1(in).either
                val rightUpdate = if (state.right.isDefined) done else p2(in).either
                leftUpdate.zipPar(rightUpdate).map {
                  case (upd1, upd2) => {
                    val tmpState = upd1.fold(x => state.copy(left = Some(x)), _ => state)
                    upd2.fold(x => tmpState.copy(right = Some(x)), _ => tmpState)
                  }
                }
              }

              newState.flatMap(s => extract(s).tap(_ => if (s eq state) UIO.unit else ref.set(s)))

          }
      }
    } yield push)
  }
}
