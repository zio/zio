---
id: build-todomvc-with-zio-scalajs
title: "Build a TodoMVC Application with ZIO on Scala.js"
description: "Create a browser-based TodoMVC app with ZIO state management, eliminating callback spaghetti and enforcing type safety."
keywords:
  - "Scala.js Browser Apps"
  - "State Management"
  - "DOM Event Handling"
  - "Ref"
  - "ZIOAppDefault"
---

## Introduction

By the end of this guide, you will have a working TodoMVC application running in the browser with full type safety, atomic state mutations, and composable event handling. The approach eliminates imperative DOM manipulation scattered across event callbacks and replaces it with structured ZIO effects: define your state as a `Ref`, render UI from that state, and wire event handlers to state mutations. This makes your application's behavior obvious and prevents entire classes of bugs that plague callback-heavy code.

## The Problem

Building interactive browser apps with Scala.js often devolves into imperative JavaScript patterns — mutating global state in event callbacks, manually calling render functions after every change, and struggling to keep the DOM in sync with your actual data.

Consider building a todo list without a structured approach: you maintain a mutable list, attach click handlers to buttons, and call a render function from each handler. If you forget to re-render after an update, the UI lies. If two handlers run concurrently, they corrupt the list. You end up with race conditions and scattered `render()` calls that must stay in sync by hand.

Here's what that looks like today:

```scala mdoc:compile-only
// Global mutable state — unsafe in async context
var todos: scala.collection.mutable.ListBuffer[String] = scala.collection.mutable.ListBuffer()

// Event handler 1: scattered across the page, manual render call
def addTodoHandler(title: String): Unit = {
  if (title.nonEmpty) {
    todos += title  // UNSAFE: no atomicity, other handlers might interleave
    // Manually re-render — easy to forget, hard to scale
    val html = todos.map(t => s"<li>$t</li>").mkString("\n")
    println(html)  // In real code: render to DOM
  }
}

// Event handler 2: doing the same thing differently
def deleteTodoHandler(index: Int): Unit = {
  todos.remove(index)  // Order matters; list shifted, indices invalidated
  
  // Re-render again, same code, manually
  val html = todos.map(t => s"<li>$t</li>").mkString("\n")
  println(html)  // In real code: render to DOM
}

// Concurrent execution: both handlers run "simultaneously" (async callbacks)
// If deleteTodoHandler runs between the read and write in addTodoHandler,
// the mutations interleave and state corruption occurs.
addTodoHandler("Learn ZIO")
deleteTodoHandler(0)
addTodoHandler("Build an app")
println(s"Final state: $todos")  // Unpredictable order; may lose todos
```

The pain is obvious: mutable state shared across multiple callbacks, duplicated render logic in each handler, no atomicity guarantees, and the DOM can fall out of sync with data at any point.

## Prerequisites

Add the ZIO library and Scala.js DOM bindings to your project:

```scala
libraryDependencies ++= Seq(
  "dev.zio"        %%% "zio"           % "@VERSION@",
  "org.scala-js"   %%% "scalajs-dom"   % "2.8.1"
)
```

Ensure you have the Scala.js sbt plugins in `project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")
```

Configure your `build.sbt` to enable Scala.js compilation and the browser entry point (covered in the first capability section).

You already understand ZIO basics on the JVM — effects, `Ref`, `for` comprehensions — and want to apply them to the browser. You have a passing familiarity with DOM APIs and HTML.

## The Core Model

Define the types your TodoMVC application will work with:

```scala mdoc:silent
final case class Todo(
  id: String,
  title: String,
  done: Boolean
)

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
```

`AppState` holds the complete UI state: the list of todos (each with a unique `id`, `title`, and `done` flag) and the currently selected filter. All mutations flow through a `Ref[AppState]`, ensuring atomicity and type safety.

## Set Up Scala.js with ZIO

Scala.js requires explicit sbt configuration to cross-compile your code to JavaScript and set up a browser entry point. Your build needs the plugins loaded, and your code module must enable the main initializer so the browser can run your ZIO app.

Define a cross-project that compiles to both JVM and JS (in `build.sbt`):

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/ProjectSetupExample.scala
```

The key flag `scalaJSUseMainModuleInitializer := true` tells the Scala.js compiler to generate a `main()` function that runs immediately in the browser, starting your ZIO runtime.

Confirm compilation succeeds:

```bash
sbt examplesJS/fastLinkJS
```

You should see no errors and a compiled JS file at `examples/js/target/scala-2.13/examples-fastopt/main.js`. Open the HTML file (`index.html`, provided in the example) in a browser and check the DevTools console for output.

## Create the Entry Point

On the JVM, ZIO apps extend `ZIOAppDefault` and implement `run: ZIO[ZIOAppArgs with Scope, Any, Any]`. On Scala.js, the same trait works, but the runtime and constraints differ: there are no threads, no blocking I/O, and the effect system is purely asynchronous. You must never return from `run` until your app is ready to shut down — typically, this means calling `ZIO.never` to block forever.

Implement the entry point and wire up state and handlers:

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/EntryPointExample.scala
```

The `for` comprehension initializes a `Ref[AppState]`, renders the initial state to the DOM, wires all event handlers, and then calls `ZIO.never` to keep the application alive. When the browser closes the tab or navigates away, the app terminates naturally.

Compile and check the browser console: you should see the startup message.

## Model State with Ref

All state mutations happen through a single `Ref[AppState]`. Each event handler reads the current state, computes an update, and atomically swaps the old state for the new one. `Ref.modify` ensures that the read-modify-write cycle is atomic — no interleaving race conditions.

Define the core state mutation functions:

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/StateManagementExample.scala
```

Each function takes the `Ref`, reads it, transforms the state, and returns both the result (usually the new state for re-rendering) and the updated state. Because these operations happen inside `Ref.modify`, they are atomic: no concurrent handler can see a partially updated state.

Test by adding multiple todos in rapid succession in the browser — no race conditions, all todos appear, and the state remains consistent.

## Render UI Components

Rendering must be composable: separate functions for the todo list, footer counts, and filter buttons. Each takes the current `AppState` and mutates the DOM to match. Rather than a single monolithic render, break it into concerns, so updating one piece (like filter buttons) doesn't require touching the others.

Define rendering functions:

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/RenderingExample.scala
```

Call `Render.main(container, state)` after any state change to update the entire UI. The individual render functions are small and easy to reason about — each one knows exactly which DOM elements it owns and how to update them based on the current `AppState`.

Confirm: add a todo, toggle its done flag, delete it, click filter buttons — the UI updates correctly each time.

## Wire Event Handlers

DOM event listeners are synchronous callbacks that expect a return type of `Unit`. ZIO effects are async. The bridge is to fork the effect onto the ZIO runtime from within the callback, so the event handler returns immediately and the effect runs without blocking the browser.

Implement all event handlers:

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/EventHandlersExample.scala
```

Each handler uses the `onEvent` helper to fork a ZIO effect. Inside the effect, you read the DOM (input values, element attributes), mutate the state via `Ref.modify`, and clean up (clear input, etc.). This example shows the core pattern of state mutation. The complete pattern — including DOM re-rendering after each state change — is shown in the "Putting It Together" section.

In the full TodoMVC app, each state mutation is immediately followed by a call to `Render.main()` to update the DOM. This keeps the UI in sync with your state changes and prevents stale data from appearing on screen.

## Putting It Together

Here is the complete TodoMVC application combining all capabilities:

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/CompleteExample.scala
```

This single file defines the domain types, the main entry point, rendering logic, state mutations, and all event handlers. It is self-contained and ready to compile and run in the browser.

## Running the Examples

Clone the ZIO repository and navigate to the examples directory:

```bash
git clone https://github.com/zio/zio.git
cd zio/examples
```

<details open><summary>Step 5: Set Up Scala.js with ZIO</summary>

This step configures sbt to cross-compile your code to JavaScript and sets up the browser entry point.

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/ProjectSetupExample.scala:show-line-numbers
```

Run:

```bash
sbt examplesJS/fastLinkJS
```

Expected output:
- Compilation succeeds with no errors.
- `examples/js/target/scala-2.13/examples-fastopt/main.js` is generated.
- Opening `index.html` in a browser shows no JavaScript errors in the console.

</details>

<details><summary>Step 6: Create the Entry Point</summary>

This step implements `ZIOAppDefault` for the browser and wires the state and handlers.

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/EntryPointExample.scala:show-line-numbers
```

Run:

```bash
sbt examplesJS/fastLinkJS
```

Expected output:
- Compilation succeeds.
- Browser DevTools console shows startup messages: `TodoMVC starting...` and `TodoMVC ready!`

</details>

<details><summary>Step 7: Model State with Ref</summary>

This step implements atomic state mutations using `Ref.modify`.

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/StateManagementExample.scala:show-line-numbers
```

Run:

```bash
sbt examplesJS/fastLinkJS
```

Expected output:
- Compilation succeeds.
- State functions are available for handlers to call.
- Rapid state mutations (e.g., clicking "Add" multiple times quickly) produce no race conditions.

</details>

<details><summary>Step 8: Render UI Components</summary>

This step implements composable rendering functions that update the DOM based on state changes.

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/RenderingExample.scala:show-line-numbers
```

Run:

```bash
sbt examplesJS/fastLinkJS
```

Expected output:
- Compilation succeeds.
- Opening `index.html` shows the initial empty todo list.
- The footer and filter buttons are rendered with correct styling.

</details>

<details><summary>Step 9: Wire Event Handlers</summary>

This step connects DOM event listeners to state mutations and rendering.

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/EventHandlersExample.scala:show-line-numbers
```

Run:

```bash
sbt examplesJS/fastLinkJS
```

Expected output:
- Compilation succeeds.
- All DOM event listeners are attached without errors.
- DevTools console shows no "event handler" errors (unless intentionally testing error cases).

</details>

<details><summary>Complete TodoMVC Application</summary>

This is the full, integrated TodoMVC application combining all steps.

```scala mdoc:embed:examples/js/src/main/scala/zio/examples/scalajs_todomvc/CompleteExample.scala:show-line-numbers
```

Run:

```bash
sbt examplesJS/fastLinkJS
```

Then serve the HTML file with a local web server:

```bash
python3 -m http.server 8080
```

Open `http://localhost:8080` in your browser.

Expected output:
- Compilation succeeds.
- Browser shows an interactive todo list.
- Adding text and clicking "Add" creates a todo.
- Clicking the checkbox toggles the todo's done state (strikethrough appears).
- Clicking the × button deletes the todo.
- Filter buttons highlight the active filter and update the visible list.
- "Clear completed" removes all done todos.
- Console shows no errors.

</details>

## Going Further

This guide covered the core: setting up Scala.js, modeling state with `Ref`, rendering, and wiring event handlers. For more:

- **ZIO reference**: Read about [`Ref`](../reference/concurrency/ref.md) for other atomic operations and state management patterns.
- **Scala.js FFI**: Explore [Scala.js external method definitions](https://www.scala-js.org/doc/interoperability/calling-javascript.html) to call JavaScript libraries and use native APIs.
- **ZIO-based frontend frameworks**: Investigate [`ZIO-themed UI libraries`](https://zio.dev/ecosystem) for higher-level abstractions (e.g., composable components, routing, middleware).
- **Asynchronous patterns**: Deepen your understanding of how `Runtime.default.unsafe.fork` works and when to use `ZIO.never` vs. explicit shutdown.

</content>
</invoke>