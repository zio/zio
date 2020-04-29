package zio.stream

import zio._

object StreamUtils {
  def nPulls[R, E, A](pull: ZIO[R, Option[E], Chunk[A]], n: Int): ZIO[R, Nothing, List[Either[Option[E], Chunk[A]]]] =
    ZIO.foreach(1 to n)(_ => pull.either)
}
