package scalasjs

import zio.test.*

object FilterSpec extends ZIOSpecDefault:

  private val todos = List(
    Todo("1", "buy milk", completed = false),
    Todo("2", "walk dog", completed = true),
    Todo("3", "write code", completed = false)
  )

  def spec = suite("Filter")(
    test("All returns every todo") {
      assertTrue(Filter(todos, Filter.All) == todos)
    },
    test("Active returns only incomplete todos") {
      assertTrue(Filter(todos, Filter.Active) == List(todos(0), todos(2)))
    },
    test("Completed returns only completed todos") {
      assertTrue(Filter(todos, Filter.Completed) == List(todos(1)))
    },
    test("fromHash maps known hashes") {
      assertTrue(
        Filter.fromHash("#/active") == Filter.Active,
        Filter.fromHash("#/completed") == Filter.Completed,
        Filter.fromHash("#/") == Filter.All,
        Filter.fromHash("") == Filter.All,
        Filter.fromHash("#/unknown") == Filter.All
      )
    }
  )
