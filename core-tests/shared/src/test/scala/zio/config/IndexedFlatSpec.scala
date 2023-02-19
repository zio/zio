package zio.config.experimental

import zio._
import zio.test._
import zio.test.Assertion._

object IndexedFlatSpec extends ZIOBaseSpec {

  def spec = suite("IndexedFlatSpec") {
    test("indexed sequence simple") {
      val configProvider = fromIndexedMap(Map("id[0]" -> "1", "id[1]" -> "2", "id[2]" -> "3"))
      val config         = Config.listOf("id", Config.int)

      for {
        result <- configProvider.load(config)
      } yield assertTrue(result == List(1, 2, 3))
    } +
      test("indexed sequence of one product") {
        val configProvider = fromIndexedMap(Map("employees[0].age" -> "1", "employees[0].id" -> "1"))
        val product        = Config.int("age").zip(Config.int("id"))
        val config         = Config.listOf("employees", product)

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == List((1, 1)))
      } +
      test("indexed sequence of one product with missing field") {
        val configProvider = fromIndexedMap(Map("employees[0].age" -> "1"))
        val product        = Config.int("age").zip(Config.int("id"))
        val config         = Config.listOf("employees", product)

        for {
          exit <- configProvider.load(config).exit
        } yield assert(exit)(failsWithA[Config.Error])
      } +
      test("indexed sequence of multiple products") {
        val configProvider = fromIndexedMap(
          Map(
            "employees[0].age" -> "1",
            "employees[0].id"  -> "2",
            "employees[1].age" -> "3",
            "employees[1].id"  -> "4"
          )
        )
        val product = Config.int("age").zip(Config.int("id"))
        val config  = Config.listOf("employees", product)

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == List((1, 2), (3, 4)))
      } +
      test("indexed sequence of multiple products with missing fields") {
        val configProvider = fromIndexedMap(
          Map("employees[0].age" -> "1", "employees[0].id" -> "2", "employees[1].age" -> "3", "employees[1]" -> "4")
        )
        val product = Config.int("age").zip(Config.int("id"))
        val config  = Config.listOf("employees", product)

        for {
          exit <- configProvider.load(config).exit
        } yield assert(exit)(failsWithA[Config.Error])
      } +
      test("indexed sequence of multiple products with optional fields") {
        val configProvider =
          fromIndexedMap(
            Map("employees[0].age" -> "1", "employees[0].id" -> "2", "employees[1].id" -> "4")
          )
        val product = Config.int("age").optional.zip(Config.int("id"))
        val config  = Config.listOf("employees", product)

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == List((Some(1), 2), (None, 4)))
      } +
      test("product of indexed sequences with reusable config") {
        val configProvider = fromIndexedMap(
          Map(
            "employees[0].id"  -> "0",
            "employees[1].id"  -> "1",
            "employees[0].age" -> "10",
            "employees[1].age" -> "11",
            "students[0].id"   -> "20",
            "students[1].id"   -> "30",
            "students[0].age"  -> "2",
            "students[1].age"  -> "3"
          )
        )
        val idAndAge = Config.int("id").zip(Config.int("age"))
        val config   = Config.listOf("employees", idAndAge).zip(Config.listOf("students", idAndAge))

        for {
          result           <- configProvider.load(config)
          expectedEmployees = List((0, 10), (1, 11))
          expectedStudents  = List((20, 2), (30, 3))
        } yield assertTrue(result == expectedEmployees -> expectedStudents)
      } +
      test("map of indexed sequence") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments.department1.employees[0].age" -> "10",
              "departments.department1.employees[0].id"  -> "0",
              "departments.department1.employees[1].age" -> "20",
              "departments.department1.employees[1].id"  -> "1",
              "departments.department2.employees[0].age" -> "10",
              "departments.department2.employees[0].id"  -> "0",
              "departments.department2.employees[1].age" -> "20",
              "departments.department2.employees[1].id"  -> "1"
            )
          )

        val employee = Config.int("age").zip(Config.int("id"))

        val config = Config.table("departments", Config.listOf("employees", employee))

        for {
          result           <- configProvider.load(config)
          expectedEmployees = List((10, 0), (20, 1))
        } yield assertTrue(result == Map("department1" -> expectedEmployees, "department2" -> expectedEmployees))
      } +
      test("indexed sequence of map") {
        val configProvider =
          fromIndexedMap(
            Map(
              "employees[0].details.age" -> "10",
              "employees[0].details.id"  -> "0",
              "employees[1].details.age" -> "20",
              "employees[1].details.id"  -> "1"
            )
          )

        val employee = Config.table("details", Config.int)
        val config   = Config.listOf("employees", employee)

        for {
          result           <- configProvider.load(config)
          expectedEmployees = List(Map("age" -> 10, "id" -> 0), Map("age" -> 20, "id" -> 1))
        } yield assertTrue(result == expectedEmployees)
      } +
      test("indexed sequence of indexed sequence") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments[0].employees[0].age" -> "10",
              "departments[0].employees[0].id"  -> "0",
              "departments[0].employees[1].age" -> "20",
              "departments[0].employees[1].id"  -> "1",
              "departments[1].employees[0].age" -> "10",
              "departments[1].employees[0].id"  -> "0",
              "departments[1].employees[1].age" -> "20",
              "departments[1].employees[1].id"  -> "1"
            )
          )

        val employee = Config.int("age").zip(Config.int("id"))

        val department = Config.listOf("employees", employee)

        val config = Config.listOf("departments", department)

        for {
          result             <- configProvider.load(config)
          expectedEmployees   = List((10, 0), (20, 1))
          expectedDepartments = List(expectedEmployees, expectedEmployees)
        } yield assertTrue(result == expectedDepartments)
      } +
      test("empty list") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments" -> ""
            )
          )

        val config = Config.listOf("departments", Config.int)

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == Nil)
      } +
      test("empty list optional") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments" -> ""
            )
          )

        val config = Config.listOf("departments", Config.int.optional)

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == Nil)
      } +
      test("empty list with description") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments" -> ""
            )
          )

        val config = Config.listOf("departments", Config.int ?? "Integer")

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == Nil)
      } +
      test("empty list with product") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments" -> ""
            )
          )

        val member = Config.int("age").zip(Config.string("name"))

        val config = Config.listOf("departments", member)

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == Nil)
      } +
      test("empty list with product optional") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments" -> ""
            )
          )

        val member = Config.int("age").zip(Config.string("name"))

        val config = Config.listOf("departments", member.optional)

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == Nil)
      } +
      test("empty list with product with description") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments" -> ""
            )
          )

        val member = Config.int("age").zip(Config.string("name"))

        val config = Config.listOf("departments", member ?? "Member")

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == Nil)
      } +
      test("empty list within indexed list") {
        val configProvider =
          fromIndexedMap(
            Map(
              "departments[0].ids"    -> "",
              "departments[1].ids[0]" -> "1",
              "departments[2].ids[0]" -> "1",
              "departments[2].ids[1]" -> "2"
            )
          )

        val config = Config.listOf("departments", Config.listOf("ids", Config.int))

        for {
          result <- configProvider.load(config)
        } yield assertTrue(result == List(Nil, List(1), List(1, 2)))
      }

  }

}
