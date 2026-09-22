package scalasjs

import zio.json.*
import zio.test.*

object TodoJsonSpec extends ZIOSpecDefault:

  def spec = suite("Todo JSON codec")(
    test("round-trips a single todo") {
      val todo = Todo("abc-123", "buy milk", completed = false)
      assertTrue(todo.toJson.fromJson[Todo] == Right(todo))
    },
    test("round-trips completed = true") {
      val todo = Todo("abc-124", "walk dog", completed = true)
      assertTrue(todo.toJson.fromJson[Todo] == Right(todo))
    },
    test("round-trips a unicode title") {
      val todo = Todo("abc-125", "buy café ☕ crème", completed = false)
      assertTrue(todo.toJson.fromJson[Todo] == Right(todo))
    },
    test("round-trips an empty list") {
      val todos = List.empty[Todo]
      assertTrue(todos.toJson.fromJson[List[Todo]] == Right(todos))
    },
    test("round-trips a list of todos") {
      val todos = List(
        Todo("1", "a", completed = false),
        Todo("2", "b", completed = true)
      )
      assertTrue(todos.toJson.fromJson[List[Todo]] == Right(todos))
    }
  )
