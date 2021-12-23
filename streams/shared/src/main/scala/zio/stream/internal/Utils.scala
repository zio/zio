package zio.stream.internal

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

object Utils {
  def zipChunks[A, B, C](cl: Chunk[A], cr: Chunk[B], f: (A, B) => C): (Chunk[C], Either[Chunk[A], Chunk[B]]) =
    if (cl.size > cr.size)
      (cl.take(cr.size).zipWith(cr)(f), Left(cl.drop(cr.size)))
    else
      (cl.zipWith(cr.take(cl.size))(f), Right(cr.drop(cl.size)))

  def zipLeftChunks[A, B](cl: Chunk[A], cr: Chunk[B]): (Chunk[A], Either[Chunk[A], Chunk[B]]) =
    if (cl.size > cr.size)
      (cl.take(cr.size), Left(cl.drop(cr.size)))
    else
      (cl, Right(cr.drop(cl.size)))

  def zipRightChunks[A, B](cl: Chunk[A], cr: Chunk[B]): (Chunk[B], Either[Chunk[A], Chunk[B]]) =
    if (cl.size > cr.size)
      (cr, Left(cl.drop(cr.size)))
    else
      (cr.take(cl.size), Right(cr.drop(cl.size)))
}
