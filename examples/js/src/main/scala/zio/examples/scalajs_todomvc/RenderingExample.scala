package zio.examples.scalajs_todomvc

import org.scalajs.dom
import org.scalajs.dom.Element
import zio._
import zio.Trace

/**
 * RenderingExample
 *
 * Demonstrates composable rendering functions that update the DOM based on
 * state changes.
 *
 * Rendering must be composable: separate functions for the todo list, footer
 * counts, and filter buttons. Each takes the current `AppState` and mutates the
 * DOM to match. Rather than a single monolithic render, break it into concerns,
 * so updating one piece (like filter buttons) doesn't require touching the
 * others.
 *
 * Call `Render.main(container, state)` after any state change to update the
 * entire UI.
 *
 * Run with: sbt appJS/fastLinkJS
 *
 * Expected behavior:
 *   - Add a todo, toggle its done flag, delete it, click filter buttons — the
 *     UI updates correctly.
 *   - Filter buttons highlight the active filter.
 *   - Footer shows count of remaining active todos.
 */
object RenderingExample {

  // Domain types
  final case class Todo(id: String, title: String, done: Boolean)

  sealed trait Filter
  object Filter {
    case object All       extends Filter
    case object Active    extends Filter
    case object Completed extends Filter
  }

  final case class AppState(todos: List[Todo], filter: Filter)

  object Render {

    // Main entry point: render all components
    def main(container: Element, state: AppState)(implicit trace: Trace): UIO[Unit] =
      for {
        _ <- todoList(container, state.todos)
        _ <- footer(container, state)
        _ <- filters(container, state.filter)
      } yield ()

    // Render the todo list items
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

    // Render footer with counts
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

    // Render filter buttons and highlight the active filter
    private def filters(container: Element, currentFilter: Filter)(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed {
        val filtersEl = container.querySelector("#filters").asInstanceOf[dom.HTMLElement]

        def filterBtn(f: Filter, label: String): String = {
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

    // Escape HTML special characters to prevent XSS
    private def escapeHtml(text: String): String =
      text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
  }
}
