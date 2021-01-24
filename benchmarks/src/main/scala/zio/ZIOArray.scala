package zio

import scala.{Array, Boolean, Int, Unit}

object ZIOArray {

  def bubbleSort[A](lessThanEqual0: (A, A) => Boolean)(array: Array[A]): UIO[Unit] = {
    def outerLoop(i: Int): UIO[Unit] =
      if (i >= array.length - 1) UIO.unit else innerLoop(i, i + 1).flatMap(_ => outerLoop(i + 1))

    def innerLoop(i: Int, j: Int): UIO[Unit] =
      if (j >= array.length) UIO.unit
      else
        UIO((array(i), array(j))).flatMap { case (ia, ja) =>
          val maybeSwap = if (lessThanEqual0(ia, ja)) UIO.unit else swapIJ(i, ia, j, ja)

          maybeSwap.flatMap(_ => innerLoop(i, j + 1))
        }

    def swapIJ(i: Int, ia: A, j: Int, ja: A): UIO[Unit] =
      UIO { array.update(i, ja); array.update(j, ia) }

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

object MonixIOArray {
  import monix.eval.Task

  def bubbleSort[A](lessThanEqual0: (A, A) => Boolean)(array: Array[A]): Task[Unit] = {
    def outerLoop(i: Int): Task[Unit] =
      if (i >= array.length - 1) Task.unit else innerLoop(i, i + 1).flatMap(_ => outerLoop(i + 1))

    def innerLoop(i: Int, j: Int): Task[Unit] =
      if (j >= array.length) Task.unit
      else
        Task.eval((array(i), array(j))).flatMap { case (ia, ja) =>
          val maybeSwap = if (lessThanEqual0(ia, ja)) Task.unit else swapIJ(i, ia, j, ja)

          maybeSwap.flatMap(_ => innerLoop(i, j + 1))
        }

    def swapIJ(i: Int, ia: A, j: Int, ja: A): Task[Unit] =
      Task.eval { array.update(i, ja); array.update(j, ia) }

    outerLoop(0)
  }
}
