package zio.test

import zio.ZIO
import zio.internal.macros.StringUtils.StringOps

object PrettyPrintSpec extends ZIOBaseSpec {

  def spec = suite("PrettyPrint")(
    test("String") {
      assertTrue(
        PrettyPrint("A String").unstyled == "\"A String\"",
        PrettyPrint(
          "A String with a \" will be well formatted"
        ).unstyled == "\"A String with a \\\" will be well formatted\"",
        PrettyPrint(
          "A String with a \n will use 3 quotes"
        ).unstyled == "\"\"\"A String with a \n will use 3 quotes\"\"\""
      )
    },
    test("List") {
      assertTrue(PrettyPrint(List(1, 2, 3)).unstyled == "List(1, 2, 3)")
    },
    test("List of String") {
      assertTrue(PrettyPrint(List("1", "2", "3")).unstyled == "List(\"1\", \"2\", \"3\")")
    },
    test("Array of String") {
      assertTrue(PrettyPrint(Array("1", "2", "3")).unstyled == "Array(\"1\", \"2\", \"3\")")
    },
    test("Map") {
      val expected = """
Map(
  "name" -> "Biff",
  "age" -> 123,
  "inventory" -> Map(
    "food" -> "Cake",
    "candy" -> "Chocolate"
  )
)
""".trim
      assertTrue(
        PrettyPrint(
          Map("name" -> "Biff", "age" -> 123, "inventory" -> Map("food" -> "Cake", "candy" -> "Chocolate"))
        ).unstyled == expected
      )
    },
    test("Case Class - single-line") {
      final case class Person(name: String)
      val expected = """
Person(name = "Glenda")
""".trim
      assertTrue(
        PrettyPrint(Person("Glenda")).unstyled == expected
      )
    } @@ TestAspect.exceptScala212,
    test("Case Class - multi-line") {
      final case class Person(name: String, age: Int)
      val expected = """
Person(
  name = "Glenda",
  age = 123
)
""".trim
      assertTrue(
        PrettyPrint(Person("Glenda", 123)).unstyled == expected
      )
    } @@ TestAspect.exceptScala212,
    test("Huge list") {
      val list = (1 to 1000).toList
      assertTrue(PrettyPrint(list).unstyled == list.mkString("List(", ", ", ")"))
    },
    test("speed test - comes from https://github.com/zio/zio/issues/8644") {
      final case class ComplexObject(
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
        def of(i: Int): ComplexObject = {
          val s = i.toString
          ComplexObject(
            id = s,
            name = s,
            name2 = s,
            name3 = s,
            name4 = s,
            name5 = s,
            name6 = s,
            name7 = s,
            name8 = s,
            name9 = s,
            name10 = s,
            name11 = s
          )
        }
      }

      for {
        _ <- ZIO.unit
        // sample of complex objects
        hugeList = (1 to 1000).map(i => ComplexObject.of(i)).toList

        shouldNotHave20  = assertTrue(!hugeList.exists(_.name3 == "20"))
        shouldNotHave510 = assertTrue(!hugeList.exists(_.name3 == "510"))
        shouldNotHave780 = assertTrue(!hugeList.exists(_.name3 == "780"))
        shouldNotHave999 = assertTrue(!hugeList.exists(_.name3 == "999"))
      } yield TestResult.allSuccesses(
        shouldNotHave20,
        shouldNotHave510,
        shouldNotHave780,
        shouldNotHave999
      )
    } @@ TestAspect.failing
  )

}
