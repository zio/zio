package zio.test

import zio.ZIO
import zio.test.GenUtils.ExistsFaster

object ExistsTestSlowSpec extends ZIOSpecDefault {

  case class ComplexObject(
    id: String,
    name: String,
    name2: String,
    name3: String,
    name4: String,
    name5: String,
    name6: String,
    name7: String,
    name8: String,
    name9: String,
    name10: String,
    name11: String
  )

  object ComplexObject {
    def of(i: Int): ComplexObject = ComplexObject(
      id = i.toString,
      name = i.toString,
      name2 = i.toString,
      name3 = i.toString,
      name4 = i.toString,
      name5 = i.toString,
      name6 = i.toString,
      name7 = i.toString,
      name8 = i.toString,
      name9 = i.toString,
      name10 = i.toString,
      name11 = i.toString
    )
  }

  val toIndex: Int  = 10000
  val numSteps: Int = 100
  val stepInc: Int  = toIndex / numSteps
  val hugeList      = (1 to toIndex).map(i => ComplexObject.of(i)).toList

  def spec = suite("ExistsTestSlowIssue")(
    test("testSlowExistsInList") {
      for {
        _ <- ZIO.unit

        allShouldHave    = (1 to toIndex by stepInc).map(i => assertTrue(hugeList.exists(_.name3 == i.toString)))
        allShouldNotHave = (1 to toIndex by stepInc).map(i => assertTrue(!hugeList.exists(_.name4 == (-i).toString)))

      } yield TestResult.allSuccesses(
        allShouldNotHave ++ allShouldHave
      )
    },
    test("testSlowExistsInListFixed") {
      for {
        _ <- ZIO.unit

        allShouldHave = (1 to toIndex by stepInc).map(i => assertTrue(hugeList.existsFast(_.name3 == i.toString)))
        allShouldNotHave =
          (1 to toIndex by stepInc).map(i => assertTrue(!hugeList.existsFast(_.name4 == (-i).toString)))

      } yield TestResult.allSuccesses(
        allShouldNotHave ++ allShouldHave
      )
    },
    test("testSlowFixed") {
      for {
        _ <- ZIO.unit
        // sample of complex objects
        hugeList = (1 to 1000).map(i => ComplexObject.of(i)).toList

        shouldNotHave20  = assertTrue(!hugeList.existsFast(_.name3 == "-20"))
        shouldNotHave510 = assertTrue(!hugeList.existsFast(_.name3 == "-510"))
        shouldNotHave780 = assertTrue(!hugeList.existsFast(_.name3 == "-780"))
        shouldNotHave999 = assertTrue(!hugeList.existsFast(_.name3 == "-999"))
      } yield TestResult.allSuccesses(
        shouldNotHave20,
        shouldNotHave510,
        shouldNotHave780,
        shouldNotHave999
      )
    },
    test("testOrig") {
      for {
        _ <- ZIO.unit
        // sample of complex objects
        hugeList = (1 to 1000).map(i => ComplexObject.of(i)).toList

        shouldNotHave20  = assertTrue(!hugeList.exists(_.name3 == "-20"))
        shouldNotHave510 = assertTrue(!hugeList.exists(_.name3 == "-510"))
        shouldNotHave780 = assertTrue(!hugeList.exists(_.name3 == "-780"))
        shouldNotHave999 = assertTrue(!hugeList.exists(_.name3 == "-999"))
      } yield TestResult.allSuccesses(
        shouldNotHave20,
        shouldNotHave510,
        shouldNotHave780,
        shouldNotHave999
      )
    }
  ) @@ TestAspect.timed

}
