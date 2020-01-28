package zio.stream.experimental

import zio._

object ZStreamUtils {
  def nPulls[R, E, B, A](
    control: ZStream.Control[R, E, Nothing, B, A],
    n: Int
  ): ZIO[R, Nothing, List[Either[Either[E, B], A]]] =
    ZIO.foreach(1 to n)(_ => control.pull.either)
}
