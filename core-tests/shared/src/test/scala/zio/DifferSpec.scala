package zio

import zio.test._
import zio.test.TestAspect._

object DifferSpec extends ZIOSpecDefault {

  val smallInt = Gen.int(1, 100)

  def spec = suite("DifferSpec")(
    suite("chunk") {
      diffLaws(Differ.chunk[Int, Int => Int](Differ.update[Int]))(Gen.chunkOf(smallInt))
    },
    suite("either") {
      diffLaws(Differ.update[Int] <+> Differ.update[Int])(Gen.either(smallInt, smallInt))
    },
    suite("map") {
      diffLaws(Differ.map[Int, Int, Int => Int](Differ.update[Int]))(Gen.mapOf(smallInt, smallInt))
    },
    suite("set") {
      diffLaws(Differ.set[Int])(Gen.setOf(smallInt))
    },
    suite("tuple") {
      diffLaws(Differ.update[Int] <*> Differ.update[Int])(smallInt <*> smallInt)
    }
  ) @@ ignore

  def diffLaws[Environment, Value, Patch](differ: Differ[Value, Patch])(
    gen: Gen[Environment, Value]
  ): Spec[Environment with TestConfig, Nothing] =
    suite("differ laws")(
      test("combining patches is associative") {
        check(gen, gen, gen, gen) { (value1, value2, value3, value4) =>
          val patch1 = differ.diff(value1, value2)
          val patch2 = differ.diff(value2, value3)
          val patch3 = differ.diff(value3, value4)
          val left   = differ.combine(differ.combine(patch1, patch2), patch3)
          val right  = differ.combine(patch1, differ.combine(patch2, patch3))
          assertTrue(differ.patch(left)(value1) == differ.patch(right)(value1))
        }
      },
      test("combining a patch with an empty patch is an identity") {
        check(gen, gen) { (oldValue, newValue) =>
          val patch = differ.diff(oldValue, newValue)
          val left  = differ.combine(patch, differ.empty)
          val right = differ.combine(differ.empty, patch)
          assertTrue(
            differ.patch(left)(oldValue) == newValue,
            differ.patch(right)(oldValue) == newValue
          )
        }
      },
      test("diffing a value with itself returns an empty patch") {
        check(gen) { value =>
          assertTrue(differ.diff(value, value) == differ.empty)
        }
      },
      test("diffing and then patching is an identity") {
        check(gen, gen) { (oldValue, newValue) =>
          val patch = differ.diff(oldValue, newValue)
          assertTrue(differ.patch(patch)(oldValue) == newValue)
        }
      },
      test("patching with an empty patch is an identity") {
        check(gen) { value =>
          assertTrue(differ.patch(differ.empty)(value) == value)
        }
      }
    )
}
