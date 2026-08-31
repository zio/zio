package zio.examples.scalajs_todomvc

import org.scalajs.dom.{document, Element}
import zio._
import zio.Trace
import scala.annotation.unused

/**
 * EntryPointExample
 *
 * Demonstrates the ZIOAppDefault entry point for a Scala.js TodoMVC
 * application.
 *
 * Key concepts:
 *   - On the JVM, ZIO apps extend ZIOAppDefault and implement `run`.
 *   - On Scala.js, the same trait works, but the runtime constraints differ: no
 *     threads, no blocking I/O, purely asynchronous.
 *   - You must never return from `run` until your app is ready to shut down —
 *     typically, this means calling `ZIO.never`.
 *   - All state flows through a `Ref[AppState]`, and all mutations happen
 *     atomically.
 *
 * Run with: sbt appJS/fastLinkJS
 *
 * Expected output in browser DevTools console: TodoMVC starting... TodoMVC
 * ready!
 */
object EntryPointExample extends ZIOAppDefault {

  // Core domain types
  final case class Todo(id: String, title: String, done: Boolean)

  sealed trait Filter
  object Filter {
    case object All       extends Filter
    case object Active    extends Filter
    case object Completed extends Filter
  }

  final case class AppState(todos: List[Todo], filter: Filter)

  // Render function (simplified for entry point demo)
  def render(container: Element, @unused _state: AppState)(implicit trace: Trace): UIO[Unit] =
    ZIO.succeed {
      val html = s"""
        <div class="app">
          <h1>TodoMVC</h1>
          <input id="todoInput" placeholder="What needs to be done?" />
          <button id="addTodo">Add</button>
          <ul id="todoList"></ul>
        </div>
      """
      container.innerHTML = html
    }

  // Event handler setup (simplified for entry point demo)
  def setupEventHandlers(@unused _container: Element, @unused _stateRef: Ref[AppState])(implicit
    trace: Trace
  ): UIO[Unit] =
    ZIO.succeed {
      // Handlers would be wired here in the full implementation
      ()
    }

  override def run: ZIO[ZIOAppArgs with Scope, Any, Any] = {
    implicit val trace: Trace = Trace.empty
    val _container            = document.getElementById("app").asInstanceOf[Element]

    for {
      // Log startup
      _ <- Console.printLine("TodoMVC starting...")

      // Initialize state as a Ref — atomic, type-safe, shared across handlers
      stateRef <- Ref.make[AppState](AppState(List.empty, Filter.All))

      // Render initial state to the DOM
      initialState <- stateRef.get
      _            <- render(_container, initialState)

      // Wire up all event handlers
      _ <- setupEventHandlers(_container, stateRef)

      // Log readiness
      _ <- Console.printLine("TodoMVC ready!")

      // Keep app running: the browser will GC when the user navigates away or closes the tab
      _ <- ZIO.never

    } yield ExitCode.success
  }
}
