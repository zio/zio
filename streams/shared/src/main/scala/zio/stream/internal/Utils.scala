package zio.stream.internal

import zio.Chunk

object Utils {
  def zipChunks[A, B, C](cl: Chunk[A], cr: Chunk[B], f: (A, B) => C): (Chunk[C], Either[Chunk[A], Chunk[B]]) =
    if (cl.size > cr.size)
      (cl.take(cr.size).zipWith(cr)(f), Left(cl.drop(cr.size)))
    else
      (cl.zipWith(cr.take(cl.size))(f), Right(cr.drop(cl.size)))
}
