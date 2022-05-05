package zio.test

import zio.{Ref, UIO}

object TestProvideSpecTypes {

  final case class IntService(ref: Ref[Int]) {
    def add(int: Int): UIO[Int] = ref.updateAndGet(_ + int)
  }

  final case class StringService(ref: Ref[String]) {
    def append(string: String): UIO[String] = ref.updateAndGet(_ + string)
  }

}
