package zio.examples.scalajs_todomvc

import org.scalajs.dom
import org.scalajs.dom.{document, Element}
import zio._
import zio.Trace

/**
 * CompleteExample
 *
 * The complete, integrated TodoMVC application combining all capabilities: all
 * domain types, Main extending ZIOAppDefault, Render, TodoHandlers. This is the
 * full working example that runs in the browser.
 *
 * This single file defines the domain types, the main entry point, rendering
 * logic, state mutations, and all event handlers. It is self-contained and
 * ready to compile and run in the browser.
 *
 * Run with: sbt appJS/fastLinkJS
 *
 * Then serve with: python3 -m http.server 8080
 *
 * Open http://localhost:8080 in your browser.
 *
 * Expected output in DevTools console: TodoMVC starting... TodoMVC ready!
 *
 * Expected behavior in browser:
 *   - Adding text and clicking "Add" creates a todo
 *   - Clicking the checkbox toggles the todo's done state (strikethrough
 *     appears)
 *   - Clicking the × button deletes the todo
 *   - Filter buttons highlight the active filter and update the visible list
 *   - "Clear completed" removes all done todos
 */

// === Domain Models ===

final case class Todo(
  id: String,
  title: String,
  done: Boolean
)

object Todo {
  def create(title: String): Todo =
    Todo(
      id = java.util.UUID.randomUUID().toString,
      title = title,
      done = false
    )
}

sealed trait Filter
object Filter {
  case object All       extends Filter
  case object Active    extends Filter
  case object Completed extends Filter
}

final case class AppState(
  todos: List[Todo],
  filter: Filter
)

// === Main App ===

object CompleteExampleMain extends ZIOAppDefault {

  def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
    implicit val trace: Trace = Trace.empty
    val container             = document.getElementById("app").asInstanceOf[Element]

    for {
      _ <- Console.printLine("TodoMVC starting...")

      stateRef <- Ref.make[AppState](AppState(List.empty, Filter.All))

      initialState <- stateRef.get
      _            <- Render.main(container, initialState)

      _ <- TodoHandlers.setupAddTodo(stateRef, container)
      _ <- TodoHandlers.setupToggleTodos(stateRef, container)
      _ <- TodoHandlers.setupDeleteTodos(stateRef, container)
      _ <- TodoHandlers.setupFilterButtons(stateRef, container)
      _ <- TodoHandlers.setupClearCompleted(stateRef, container)

      _ <- Console.printLine("TodoMVC ready!")
      _ <- ZIO.never

    } yield ExitCode.success
  }
}

// === Rendering ===

object Render {

  def main(container: Element, state: AppState)(implicit trace: Trace): UIO[Unit] =
    for {
      _ <- todoList(container, visibleTodos(state))
      _ <- footer(container, state)
      _ <- filters(container, state.filter)
    } yield ()

  private def visibleTodos(state: AppState): List[Todo] =
    state.filter match {
      case Filter.All       => state.todos
      case Filter.Active    => state.todos.filter(!_.done)
      case Filter.Completed => state.todos.filter(_.done)
    }

  private def todoList(container: Element, todos: List[Todo])(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val listEl = container.querySelector("#todoList").asInstanceOf[dom.HTMLElement]
      val html = todos.map { todo =>
        val checked = if (todo.done) "checked" else ""
        s"""<li class="todo ${if (todo.done) "completed" else ""}" data-id="${todo.id}">
           |  <input type="checkbox" class="toggle" $checked />
           |  <label>${escapeHtml(todo.title)}</label>
           |  <button class="destroy">×</button>
           |</li>""".stripMargin
      }.mkString("\n")
      listEl.innerHTML = if (html.isEmpty) "<li><em>(no todos)</em></li>" else html
    }

  private def footer(container: Element, state: AppState)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val footerEl  = container.querySelector("#footer").asInstanceOf[dom.HTMLElement]
      val active    = state.todos.count(!_.done)
      val completed = state.todos.count(_.done)
      val plural    = if (active == 1) "item" else "items"
      val html =
        s"""<span class="todo-count"><strong>$active</strong> $plural left</span>
           |${if (completed > 0) s"""<button class="clear-completed">Clear completed ($completed)</button>""" else ""}
           |""".stripMargin
      footerEl.innerHTML = html
    }

  private def filters(container: Element, currentFilter: Filter)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val filtersEl = container.querySelector("#filters").asInstanceOf[dom.HTMLElement]
      def filterBtn(f: Filter, label: String) = {
        val cls      = if (f == currentFilter) "selected" else "filter-link"
        val dataAttr = if (f == currentFilter) "" else s"""data-filter="${f.getClass.getSimpleName}""""
        s"""<a class="$cls" $dataAttr>$label</a>"""
      }
      val html =
        filterBtn(Filter.All, "All") + " " +
          filterBtn(Filter.Active, "Active") + " " +
          filterBtn(Filter.Completed, "Completed")
      filtersEl.innerHTML = html
    }

  private def escapeHtml(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
}

// === Event Handlers ===

object TodoHandlers {

  private def onEvent(effect: ZIO[Any, Any, Unit])(implicit trace: Trace): dom.Event => Unit = { _ =>
    val logged = effect.catchAll(e => Console.printLineError(s"Event error: $e"))
    val _      = Runtime.default.unsafe.fork(logged)(Trace.empty, Unsafe.unsafe)
    ()
  }

  def setupAddTodo(stateRef: Ref[AppState], container: Element)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val addBtn = document.getElementById("addTodo").asInstanceOf[dom.HTMLElement]
      addBtn.addEventListener(
        "click",
        onEvent {
          for {
            input <- ZIO.succeed(
                       document.getElementById("todoInput").asInstanceOf[dom.HTMLInputElement]
                     )
            title <- ZIO.succeed(input.value.trim)
            _ <- if (title.nonEmpty) {
                   for {
                     newState <- stateRef.modify { state =>
                                   val todo    = Todo.create(title)
                                   val updated = state.copy(todos = state.todos :+ todo)
                                   (updated, updated)
                                 }
                     _ <- Render.main(container, newState)
                     _ <- ZIO.succeed { input.value = "" }
                   } yield ()
                 } else ZIO.unit
          } yield ()
        }
      )
    }

  def setupToggleTodos(stateRef: Ref[AppState], container: Element)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      document.addEventListener(
        "change",
        onEvent {
          for {
            target <- ZIO.succeed {
                        scala.scalajs.js.Dynamic.global.event.target.asInstanceOf[dom.HTMLElement]
                      }
            isTodoToggle <- ZIO.succeed(target.classList.contains("toggle"))
            _ <- if (isTodoToggle) {
                   val liElement = target.closest("li").asInstanceOf[dom.HTMLElement]
                   val todoId    = liElement.getAttribute("data-id")
                   for {
                     newState <- stateRef.modify { state =>
                                   val updated = state.todos.map { t =>
                                     if (t.id == todoId) t.copy(done = !t.done) else t
                                   }
                                   val newState = state.copy(todos = updated)
                                   (newState, newState)
                                 }
                     _ <- Render.main(container, newState)
                   } yield ()
                 } else ZIO.unit
          } yield ()
        }
      )
    }

  def setupDeleteTodos(stateRef: Ref[AppState], container: Element)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      document.addEventListener(
        "click",
        onEvent {
          for {
            target <- ZIO.succeed {
                        scala.scalajs.js.Dynamic.global.event.target.asInstanceOf[dom.HTMLElement]
                      }
            isDestroyBtn <- ZIO.succeed(target.classList.contains("destroy"))
            _ <- if (isDestroyBtn) {
                   val liElement = target.closest("li").asInstanceOf[dom.HTMLElement]
                   val todoId    = liElement.getAttribute("data-id")
                   for {
                     newState <- stateRef.modify { state =>
                                   val updated  = state.todos.filter(_.id != todoId)
                                   val newState = state.copy(todos = updated)
                                   (newState, newState)
                                 }
                     _ <- Render.main(container, newState)
                   } yield ()
                 } else ZIO.unit
          } yield ()
        }
      )
    }

  def setupFilterButtons(stateRef: Ref[AppState], container: Element)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      document.addEventListener(
        "click",
        onEvent {
          for {
            target <- ZIO.succeed {
                        scala.scalajs.js.Dynamic.global.event.target.asInstanceOf[dom.HTMLElement]
                      }
            isFilterLink <- ZIO.succeed(target.classList.contains("filter-link"))
            _ <- if (isFilterLink) {
                   val filterStr = target.getAttribute("data-filter")
                   val filter = filterStr match {
                     case "All"       => Filter.All
                     case "Active"    => Filter.Active
                     case "Completed" => Filter.Completed
                     case _           => Filter.All
                   }
                   for {
                     newState <- stateRef.modify { state =>
                                   val newState = state.copy(filter = filter)
                                   (newState, newState)
                                 }
                     _ <- Render.main(container, newState)
                   } yield ()
                 } else ZIO.unit
          } yield ()
        }
      )
    }

  def setupClearCompleted(stateRef: Ref[AppState], container: Element)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      document.addEventListener(
        "click",
        onEvent {
          for {
            target <- ZIO.succeed {
                        scala.scalajs.js.Dynamic.global.event.target.asInstanceOf[dom.HTMLElement]
                      }
            isClearBtn <- ZIO.succeed(target.classList.contains("clear-completed"))
            _ <- if (isClearBtn) {
                   for {
                     newState <- stateRef.modify { state =>
                                   val updated  = state.todos.filter(!_.done)
                                   val newState = state.copy(todos = updated)
                                   (newState, newState)
                                 }
                     _ <- Render.main(container, newState)
                   } yield ()
                 } else ZIO.unit
          } yield ()
        }
      )
    }
}
