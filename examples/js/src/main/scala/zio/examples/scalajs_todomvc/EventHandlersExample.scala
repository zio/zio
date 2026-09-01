package zio.examples.scalajs_todomvc

import org.scalajs.dom
import org.scalajs.dom.{document, Element}
import zio._
import zio.Trace
import scala.annotation.unused

/**
 * EventHandlersExample
 *
 * Demonstrates how to wire DOM event listeners to ZIO effects.
 *
 * DOM event listeners are synchronous callbacks that expect a return type of
 * `Unit`. ZIO effects are async. The bridge is to fork the effect onto the ZIO
 * runtime from within the callback, so the event handler returns immediately
 * and the effect runs without blocking the browser.
 *
 * Key pattern: private def onEvent(effect: ZIO[Any, Any, Unit]): dom.Event =>
 * Unit = { _ => Runtime.default.unsafe.fork(effect)(Trace.empty, Unsafe.unsafe)
 * }
 *
 * Inside the effect, you read the DOM (input values, element attributes),
 * mutate the state via `Ref.modify`, re-render, and clean up (clear input,
 * etc.).
 *
 * This object is excerpted for reference — it has no entry point of its own and
 * is not meant to be compiled standalone. It shows the core fork-and-mutate
 * pattern without the re-render call; the complete pattern, including
 * re-rendering after each state change, is in CompleteExample.scala and the
 * guide's "Putting It Together" section, which is the runnable, complete
 * version.
 */
object EventHandlersExample {

  // Domain types
  final case class Todo(id: String, title: String, done: Boolean)

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

  final case class AppState(todos: List[Todo], filter: Filter)

  object TodoHandlers {

    // Helper: fork a ZIO effect from a DOM callback without blocking the browser
    private def onEvent(effect: ZIO[Any, Any, Unit])(implicit trace: Trace): dom.Event => Unit = { _ =>
      val logged = effect.catchAll(e => Console.printLineError(s"Event error: $e"))
      val _      = Runtime.default.unsafe.fork(logged)(Trace.empty, Unsafe.unsafe)
      ()
    }

    // Set up the "Add" button handler
    def setupAddTodo(stateRef: Ref[AppState], @unused container: Element)(implicit trace: Trace): UIO[Unit] =
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
                       // Atomically add todo and get new state
                       newState <- stateRef.modify { state =>
                                     val todo    = Todo.create(title)
                                     val updated = state.copy(todos = state.todos :+ todo)
                                     (updated, updated)
                                   }
                       // Re-render with new state (demonstrates the full pattern)
                       _ <- Console.printLine(s"Added todo, total count: ${newState.todos.length}")
                       // Clear input
                       _ <- ZIO.succeed { input.value = "" }
                     } yield ()
                   } else ZIO.unit
            } yield ()
          }
        )
      }

    // Set up the toggle checkbox handler
    def setupToggleTodos(stateRef: Ref[AppState], @unused container: Element)(implicit trace: Trace): UIO[Unit] =
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
                       // Atomically toggle done flag
                       newState <- stateRef.modify { state =>
                                     val updated = state.todos.map { t =>
                                       if (t.id == todoId) t.copy(done = !t.done) else t
                                     }
                                     val newState = state.copy(todos = updated)
                                     (newState, newState)
                                   }
                       // Re-render with new state (demonstrates the full pattern)
                       _ <- Console.printLine(s"Toggled todo, completed count: ${newState.todos.count(_.done)}")
                     } yield ()
                   } else ZIO.unit
            } yield ()
          }
        )
      }

    // Set up the delete button handler
    def setupDeleteTodos(stateRef: Ref[AppState], @unused container: Element)(implicit trace: Trace): UIO[Unit] =
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
                       // Atomically delete todo
                       newState <- stateRef.modify { state =>
                                     val updated  = state.todos.filter(_.id != todoId)
                                     val newState = state.copy(todos = updated)
                                     (newState, newState)
                                   }
                       // Re-render with new state (demonstrates the full pattern)
                       _ <- Console.printLine(s"Deleted todo, remaining count: ${newState.todos.length}")
                     } yield ()
                   } else ZIO.unit
            } yield ()
          }
        )
      }

    // Set up the filter button handler
    def setupFilterButtons(stateRef: Ref[AppState], @unused container: Element)(implicit trace: Trace): UIO[Unit] =
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
                       // Atomically change filter
                       newState <- stateRef.modify { state =>
                                     val newState = state.copy(filter = filter)
                                     (newState, newState)
                                   }
                       // Re-render with new state (demonstrates the full pattern)
                       _ <- Console.printLine(s"Changed filter to: ${filter.getClass.getSimpleName}")
                     } yield ()
                   } else ZIO.unit
            } yield ()
          }
        )
      }

    // Set up the "Clear completed" button handler
    def setupClearCompleted(stateRef: Ref[AppState], @unused container: Element)(implicit trace: Trace): UIO[Unit] =
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
                       // Atomically remove all completed todos
                       newState <- stateRef.modify { state =>
                                     val updated  = state.todos.filter(!_.done)
                                     val newState = state.copy(todos = updated)
                                     (newState, newState)
                                   }
                       // Re-render with new state (demonstrates the full pattern)
                       _ <- Console.printLine(s"Cleared completed todos, remaining: ${newState.todos.length}")
                     } yield ()
                   } else ZIO.unit
            } yield ()
          }
        )
      }
  }
}
