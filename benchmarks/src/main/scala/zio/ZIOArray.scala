package zio

import scala.{Array, Boolean, Int, Unit}

object ZIOArray {

  def bubbleSort[A](lessThanEqual0: (A, A) => Boolean)(array: Array[A]): UIO[Unit] = {
    def outerLoop(i: Int): UIO[Unit] =
      if (i >= array.length - 1) ZIO.unit else innerLoop(i, i + 1).flatMap(_ => outerLoop(i + 1))

    def innerLoop(i: Int, j: Int): UIO[Unit] =
      if (j >= array.length) ZIO.unit
      else
        ZIO.succeed((array(i), array(j))).flatMap { case (ia, ja) =>
          val maybeSwap = if (lessThanEqual0(ia, ja)) ZIO.unit else swapIJ(i, ia, j, ja)

          maybeSwap.flatMap(_ => innerLoop(i, j + 1))
        }

    def swapIJ(i: Int, ia: A, j: Int, ja: A): UIO[Unit] =
      ZIO.succeed { array.update(i, ja); array.update(j, ia) }

    outerLoop(0)
  }
}

object CatsIOArray {
  import cats.effect.IO

  def bubbleSort[A](lessThanEqual0: (A, A) => Boolean)(array: Array[A]): IO[Unit] = {
    def outerLoop(i: Int): IO[Unit] =
      if (i >= array.length - 1) IO.unit else innerLoop(i, i + 1).flatMap(_ => outerLoop(i + 1))

    def innerLoop(i: Int, j: Int): IO[Unit] =
      if (j >= array.length) IO.unit
      else
        IO((array(i), array(j))).flatMap { case (ia, ja) =>
          val maybeSwap = if (lessThanEqual0(ia, ja)) IO.unit else swapIJ(i, ia, j, ja)

          maybeSwap.flatMap(_ => innerLoop(i, j + 1))
        }

    def swapIJ(i: Int, ia: A, j: Int, ja: A): IO[Unit] =
      IO { array.update(i, ja); array.update(j, ia) }

    outerLoop(0)
  }
}
