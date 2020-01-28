package zio.stream.experimental

import zio._

class ZSink[-R, +E, +I, -A, +B](
  val process: ZManaged[R, E, ZSink.Control[R, E, I, A, B]]
) extends AnyVal
    with Serializable { self =>
  import ZSink.Control

  /**
   * Maps the exit value of this sink using a ''pure'' function.
   *
   * @tparam C the value type of the new sink
   * @param f the ''pure'' transformation function
   * @return a sink that produces a transformed value
   */
  def map[C](f: B => C): ZSink[R, E, I, A, C] =
    ZSink {
      self.process.map { control =>
        Control(
          a => control.push(a).mapError(_.map(f)),
          control.query
        )
      }
    }
}

object ZSink extends Serializable {

  final case class Control[-R, +E, +I, -A, +B](
    push: A => ZIO[R, Either[E, B], Any],
    query: ZIO[R, E, I]
  )

  /**
   * Creates a sink from a scoped [[Control]].
   *
   * @tparam R the sink environment type
   * @tparam E the sink error type
   * @tparam I the sink internal state type
   * @tparam A the sink input type
   * @tparam B the sink exit value type
   * @param process the scoped control
   * @return a new sink wrapping the scoped control
   */
  def apply[R, E, I, A, B](process: ZManaged[R, E, ZSink.Control[R, E, I, A, B]]): ZSink[R, E, I, A, B] =
    new ZSink(process)
}
