package scalasjs

import org.scalajs.dom
import zio.*
import zio.json.*

final class TodoStore(todosRef: Ref[List[Todo]], filterRef: Ref[Filter]):

  def all: UIO[List[Todo]] = todosRef.get

  def filter: UIO[Filter] = filterRef.get

  def visible: UIO[List[Todo]] =
    for
      todos <- todosRef.get
      f     <- filterRef.get
    yield Filter(todos, f)

  def add(title: String): UIO[Unit] =
    val trimmed = title.trim
    if trimmed.isEmpty then ZIO.unit
    else
      val todo = Todo(TodoStore.freshId(), trimmed, completed = false)
      todosRef.update(_ :+ todo) *> persist

  def toggle(id: String): UIO[Unit] =
    todosRef.update(_.map(t => if t.id == id then t.copy(completed = !t.completed) else t)) *> persist

  def toggleAll(completed: Boolean): UIO[Unit] =
    todosRef.update(_.map(_.copy(completed = completed))) *> persist

  def edit(id: String, newTitle: String): UIO[Unit] =
    val trimmed = newTitle.trim
    if trimmed.isEmpty then delete(id)
    else todosRef.update(_.map(t => if t.id == id then t.copy(title = trimmed) else t)) *> persist

  def delete(id: String): UIO[Unit] =
    todosRef.update(_.filterNot(_.id == id)) *> persist

  def clearCompleted: UIO[Unit] =
    todosRef.update(_.filterNot(_.completed)) *> persist

  def setFilter(f: Filter): UIO[Unit] =
    filterRef.set(f)

  private def persist: UIO[Unit] =
    todosRef.get.map { todos =>
      dom.window.localStorage.setItem(TodoStore.storageKey, todos.toJson)
    }

object TodoStore:

  private val storageKey = "todos-zio-scalajs"

  private def freshId(): String =
    s"${java.lang.System.currentTimeMillis()}-${scala.util.Random.nextInt(Int.MaxValue)}"

  def make: UIO[TodoStore] =
    for
      initial   <- ZIO.succeed(loadFromStorage())
      todosRef  <- Ref.make(initial)
      filterRef <- Ref.make(Filter.fromHash(dom.window.location.hash))
    yield TodoStore(todosRef, filterRef)

  private def loadFromStorage(): List[Todo] =
    Option(dom.window.localStorage.getItem(storageKey)) match
      case Some(raw) => raw.fromJson[List[Todo]].getOrElse(Nil)
      case None      => Nil
