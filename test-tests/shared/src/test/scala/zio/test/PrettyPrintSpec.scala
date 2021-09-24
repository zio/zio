package zio.test

object PrettyPrintSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("PrettyPrint")(
    test("String") {
      assertTrue(PrettyPrint("A String") == "\"A String\"")
    },
    test("List") {
      assertTrue(PrettyPrint(List(1, 2, 3)) == "List(1, 2, 3)")
    },
    test("List of String") {
      assertTrue(PrettyPrint(List("1", "2", "3")) == "List(\"1\", \"2\", \"3\")")
    },
    test("Array of String") {
      assertTrue(PrettyPrint(Array("1", "2", "3")) == "Array(\"1\", \"2\", \"3\")")
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
        ) == expected
      )
    },
    test("Case Class") {
      final case class Person(name: String, age: Int)
      val expected = """
Person(
  name = "Glenda",
  age = 123
)
""".trim
      assertTrue(
        PrettyPrint(
          Person("Glenda", 123)
        ) == expected
      )
    } @@ TestAspect.exceptScala211 @@ TestAspect.exceptScala212
  )

}
