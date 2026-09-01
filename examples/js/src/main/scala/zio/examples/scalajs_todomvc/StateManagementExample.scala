package zio.examples.scalajs_todomvc

import zio._
import zio.Trace

/**
 * StateManagementExample
 *
 * Demonstrates atomic state mutations using `Ref.modify`.
 *
 * All state mutations happen through a single `Ref[AppState]`. Each event
 * handler reads the current state, computes an update, and atomically swaps the
 * old state for the new one. `Ref.modify` ensures that the read-modify-write
 * cycle is atomic — no interleaving race conditions.
 *
 * Key pattern: stateRef.modify { state => val newState = computeNewState(state)
 * (resultValue, newState) // Return (value, updatedState) }
 *
 * This object is excerpted for reference — its functions have no entry point of
 * their own and are not meant to be compiled standalone. They become part of
 * `Main.scala`'s state handling in the complete app; see CompleteExample.scala
 * and the guide's "Putting It Together" section for the runnable, complete
 * version.
 */
object StateManagementExample {

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

  // Add a new todo: atomically append and return the new state
  def addTodo(stateRef: Ref[AppState], title: String)(implicit trace: Trace): UIO[AppState] =
    stateRef.modify { state =>
      val newTodo  = Todo.create(title)
      val newState = state.copy(todos = state.todos :+ newTodo)
      (newState, newState) // Return (value, newState)
    }

  // Toggle a todo's done flag: atomically update and return the new state
  def toggleTodo(stateRef: Ref[AppState], id: String)(implicit trace: Trace): UIO[AppState] =
    stateRef.modify { state =>
      val updated = state.todos.map { t =>
        if (t.id == id) t.copy(done = !t.done) else t
      }
      val newState = state.copy(todos = updated)
      (newState, newState)
    }

  // Delete a todo by id: atomically filter and return the new state
  def deleteTodo(stateRef: Ref[AppState], id: String)(implicit trace: Trace): UIO[AppState] =
    stateRef.modify { state =>
      val filtered = state.todos.filter(_.id != id)
      val newState = state.copy(todos = filtered)
      (newState, newState)
    }

  // Change the filter: atomically update and return the new state
  def setFilter(stateRef: Ref[AppState], filter: Filter)(implicit trace: Trace): UIO[AppState] =
    stateRef.modify { state =>
      val newState = state.copy(filter = filter)
      (newState, newState)
    }
}
