package zio

import zio.test._

object DifferSpec extends ZIOBaseSpec {

  val smallInt = Gen.int(1, 100)

  def spec = suite("DifferSpec")(
    suite("chunk") {
      diffLaws(Differ.chunk[Int, Int => Int](Differ.update[Int]))(Gen.chunkOf(smallInt))(_ == _)
    },
    suite("either") {
      diffLaws(Differ.update[Int] <+> Differ.update[Int])(Gen.either(smallInt, smallInt))(_ == _)
    },
    suite("map") {
      diffLaws(Differ.map[Int, Int, Int => Int](Differ.update[Int]))(Gen.mapOf(smallInt, smallInt))(_ == _)
    },
    suite("set") {
      diffLaws(Differ.set[Int])(Gen.setOf(smallInt))(_ == _)
    },
    suite("tuple") {
      diffLaws(Differ.update[Int] <*> Differ.update[Int])(smallInt <*> smallInt)(_ == _)
    }
  )

  def diffLaws[Environment, Value, Patch](differ: Differ[Value, Patch])(
    gen: Gen[Environment, Value]
  )(equal: (Value, Value) => Boolean): Spec[Environment, Nothing] =
    suite("differ laws")(
      test("combining patches is associative") {
        check(gen, gen, gen, gen) { (value1, value2, value3, value4) =>
          val patch1 = differ.diff(value1, value2)
          val patch2 = differ.diff(value2, value3)
          val patch3 = differ.diff(value3, value4)
          val left   = differ.combine(differ.combine(patch1, patch2), patch3)
          val right  = differ.combine(patch1, differ.combine(patch2, patch3))
          assertTrue(equal(differ.patch(left)(value1), differ.patch(right)(value1)))
        }
      },
      test("combining a patch with an empty patch is an identity") {
        check(gen, gen) { (oldValue, newValue) =>
          val patch = differ.diff(oldValue, newValue)
          val left  = differ.combine(patch, differ.empty)
          val right = differ.combine(differ.empty, patch)
          assertTrue(
            equal(differ.patch(left)(oldValue), newValue),
            equal(differ.patch(right)(oldValue), newValue)
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
          assertTrue(equal(differ.patch(patch)(oldValue), newValue))
        }
      },
      test("patching with an empty patch is an identity") {
        check(gen) { value =>
          assertTrue(equal(differ.patch(differ.empty)(value), value))
        }
      }
    )
}
