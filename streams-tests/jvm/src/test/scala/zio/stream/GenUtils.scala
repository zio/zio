package zio.stream

import zio.test.Gen
import zio.random.Random

trait GenUtils {
  def toBoolFn[R <: Random, A] = Gen.function[R, A, Boolean](Gen.boolean)
}
