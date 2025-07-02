package zio.test

import zio.internal.macros.StringUtils.StringOps
import zio.test.TestAspect.exceptScala212

import scala.collection.mutable

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
      assertTrue(
        PrettyPrint(List(1, 2, 3)).unstyled == "List(1, 2, 3)",
        PrettyPrint(List.empty).unstyled == "Nil",
        PrettyPrint(Nil).unstyled == "Nil"
      )
    },
    test("Vector") {
      assertTrue(
        PrettyPrint(Vector(1, 2, 3)).unstyled == "Vector(1, 2, 3)",
        PrettyPrint(Vector.empty).unstyled == "Vector()"
      )
    },
    test("Array") {
      assertTrue(
        PrettyPrint(Array(1, 2, 3)).unstyled == "Array(1, 2, 3)",
        PrettyPrint(Array.empty[Int]).unstyled == "Array()"
      )
    },
    test("Set") {
      assertTrue(
        PrettyPrint(Set(1, 2, 3)).unstyled == "Set(1, 2, 3)",
        PrettyPrint(Set.empty).unstyled == "Set()"
      )
    },
    test("mutable.Set") {
      assertTrue(
        PrettyPrint(mutable.Set(1, 2, 3)).unstyled == "HashSet(1, 2, 3)",
        PrettyPrint(mutable.Set.empty).unstyled == "HashSet()"
      )
    } @@ exceptScala212,
    test("mutable.SortedSet") {
      assertTrue(
        PrettyPrint(mutable.SortedSet(1, 2, 3)).unstyled == "TreeSet(1, 2, 3)",
        PrettyPrint(mutable.SortedSet.empty[Int]).unstyled == "TreeSet()"
      )
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
    } @@ exceptScala212,
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
    } @@ exceptScala212,
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

      // sample of complex objects
      val hugeList = (1 to 1000).map(i => ComplexObject.of(i)).toList

      val shouldNotHave20  = assertTrue(!hugeList.exists(_.name3 == "20"))
      val shouldNotHave510 = assertTrue(!hugeList.exists(_.name3 == "510"))
      val shouldNotHave780 = assertTrue(!hugeList.exists(_.name3 == "780"))
      val shouldNotHave999 = assertTrue(!hugeList.exists(_.name3 == "999"))

      TestResult.allSuccesses(
        shouldNotHave20,
        shouldNotHave510,
        shouldNotHave780,
        shouldNotHave999
      )
    } @@ TestAspect.failing
  )

}
