package zio.test

import zio.internal.macros.StringUtils.StringOps
import zio.test.TestAspect.{exceptScala212, sequential}

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
    } @@ TestAspect.failing,
    suite("tuples")(
      test("Tuple1") {
        assertTrue(
          PrettyPrint(Tuple1(1)).unstyled == "(1)",
          PrettyPrint(Tuple1("hello")).unstyled == "(\"hello\")"
        )
      },
      test("Tuple2") {
        assertTrue(
          PrettyPrint((1, 2)).unstyled == "(1, 2)",
          PrettyPrint(("hello", "world")).unstyled == "(\"hello\", \"world\")"
        )
      },
      test("Tuple3") {
        assertTrue(
          PrettyPrint((1, 2, 3)).unstyled == "(1, 2, 3)",
          PrettyPrint(("a", "b", "c")).unstyled == "(\"a\", \"b\", \"c\")"
        )
      },
      test("Tuple4") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4)).unstyled == "(1, 2, 3, 4)"
        )
      },
      test("Tuple5") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4, 5)).unstyled == "(1, 2, 3, 4, 5)"
        )
      },
      test("Tuple6") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4, 5, 6)).unstyled == "(1, 2, 3, 4, 5, 6)"
        )
      },
      test("Tuple7") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4, 5, 6, 7)).unstyled == "(1, 2, 3, 4, 5, 6, 7)"
        )
      },
      test("Tuple8") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4, 5, 6, 7, 8)).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8)"
        )
      },
      test("Tuple9") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4, 5, 6, 7, 8, 9)).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9)"
        )
      },
      test("Tuple10") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4, 5, 6, 7, 8, 9, 10)).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)"
        )
      },
      test("Tuple11") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)"
        )
      },
      test("Tuple12") {
        assertTrue(
          PrettyPrint((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)"
        )
      },
      test("Tuple13") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)"
        )
      },
      test("Tuple14") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)"
        )
      },
      test("Tuple15") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)"
        )
      },
      test("Tuple16") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)"
        )
      },
      test("Tuple17") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)"
        )
      },
      test("Tuple18") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)"
        )
      },
      test("Tuple19") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)"
        )
      },
      test("Tuple20") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)"
        )
      },
      test("Tuple21") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)"
        )
      },
      test("Tuple22") {
        assertTrue(
          PrettyPrint(
            (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
          ).unstyled == "(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)"
        )
      },
      test("Mixed type tuple") {
        assertTrue(
          PrettyPrint((1, "hello", 3.14, true, List(1, 2))).unstyled == "(1, \"hello\", 3.14, true, List(1, 2))"
        )
      },
      test("Nested tuples") {
        assertTrue(
          PrettyPrint(((1, 2), (3, 4))).unstyled == "((1, 2), (3, 4))",
          PrettyPrint((1, (2, 3), 4)).unstyled == "(1, (2, 3), 4)"
        )
      }
    ) @@ sequential
  )

}
