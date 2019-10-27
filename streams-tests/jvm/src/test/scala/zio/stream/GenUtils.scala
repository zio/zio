package zio.stream

import zio.test.Gen
import zio.random.Random

trait GenUtils {
  def toBoolFn[R <: Random, A] = Gen.function[R, A, Boolean](Gen.boolean)

  val stringGen = Gen.small(Gen.stringN(_)(Gen.alphaNumericChar))

  val intGen = Gen.int(-10, 10)
}
