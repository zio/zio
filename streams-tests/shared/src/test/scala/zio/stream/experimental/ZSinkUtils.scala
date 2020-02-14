package zio.stream.experimental

import ZSink.Control

import zio._

object ZSinkUtils {
  val initErrorSink = ZSink[Any, String, Int, Int, Int] {
    Managed.failNow("Ouch")
  }

  val stepErrorSink = ZSink[Any, String, Int, Int, Int] {
    Managed.succeedNow {
      Control(
        _ => IO.failNow(Left("Ouch")),
        IO.failNow("Ouch")
      )
    }
  }

  def extractErrorSink = ZSink[Any, String, Int, Int, Int] {
    Managed.succeedNow {
      Control(
        _ => IO.unit,
        IO.failNow("Ouch")
      )
    }
  }

  def sinkIteration[R, E, M, B, A](sink: ZSink[R, E, B, A, Any], a: A): ZIO[R, E, B] =
    sink.process.use(control => control.push(a).catchAll(_.fold(IO.fail(_), _ => IO.unit)) *> control.query)
}
