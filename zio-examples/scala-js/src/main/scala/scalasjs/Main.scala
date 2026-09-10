package scalasjs

import org.scalajs.dom
import org.scalajs.dom.html
import zio.*

object Main:

  private val runtime = Runtime.default

  private def runFireAndForget(effect: UIO[Unit]): Unit =
    Unsafe.unsafe { implicit unsafe => runtime.unsafe.fork(effect) }

  def main(args: Array[String]): Unit =
    runFireAndForget(
      for
        store <- TodoStore.make
        _     <- ZIO.succeed(wire(store))
        _     <- rerender(store)
      yield ()
    )

  private def wire(store: TodoStore): Unit =
    val newTodo        = dom.document.getElementById("new-todo").asInstanceOf[html.Input]
    val toggleAll      = dom.document.getElementById("toggle-all").asInstanceOf[html.Input]
    val clearCompleted = dom.document.getElementById("clear-completed").asInstanceOf[html.Button]

    newTodo.onkeydown = (e: dom.KeyboardEvent) =>
      if e.key == "Enter" then
        val title = newTodo.value
        newTodo.value = ""
        runFireAndForget(store.add(title) *> rerender(store))

    toggleAll.onclick = (_: dom.Event) =>
      runFireAndForget(store.toggleAll(toggleAll.checked) *> rerender(store))

    clearCompleted.onclick = (_: dom.Event) =>
      runFireAndForget(store.clearCompleted *> rerender(store))

    Router.onChange { f =>
      runFireAndForget(store.setFilter(f) *> rerender(store))
    }

  private def rerender(store: TodoStore): UIO[Unit] =
    for
      all     <- store.all
      visible <- store.visible
      f       <- store.filter
      _       <- ZIO.succeed(paint(store, all, visible, f))
    yield ()

  private def paint(store: TodoStore, all: List[Todo], visible: List[Todo], filter: Filter): Unit =
    val todoList       = dom.document.getElementById("todo-list").asInstanceOf[html.UList]
    val todoCount      = dom.document.getElementById("todo-count").asInstanceOf[html.Span]
    val clearCompleted = dom.document.getElementById("clear-completed").asInstanceOf[html.Button]
    val toggleAll      = dom.document.getElementById("toggle-all").asInstanceOf[html.Input]
    val filters        = dom.document.getElementById("filters")

    todoList.textContent = ""
    visible.foreach { todo =>
      val li = View.renderTodoItem(
        todo,
        onToggle = id => runFireAndForget(store.toggle(id) *> rerender(store)),
        onDestroy = id => runFireAndForget(store.delete(id) *> rerender(store)),
        onCommitEdit = (id, title) => runFireAndForget(store.edit(id, title) *> rerender(store))
      )
      todoList.appendChild(li)
    }

    val activeCount = all.count(!_.completed)
    todoCount.textContent = View.renderItemCount(activeCount)

    val hasCompleted = all.exists(_.completed)
    clearCompleted.style.display = if hasCompleted then "block" else "none"

    toggleAll.checked = all.nonEmpty && all.forall(_.completed)

    val filterLinks = filters.querySelectorAll("a")
    for i <- 0 until filterLinks.length do
      val a = filterLinks(i).asInstanceOf[html.Anchor]
      if Filter.fromHash(a.getAttribute("href")) == filter then a.className = "selected"
      else a.className = ""
