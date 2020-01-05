package zio.stream

import zio.random.Random
import zio.test.Gen

trait GenUtils {
  def toBoolFn[R <: Random, A] = Gen.function[R, A, Boolean](Gen.boolean)

  val stringGen = Gen.small(Gen.stringN(_)(Gen.alphaNumericChar))

  val intGen = Gen.int(-10, 10)
}
