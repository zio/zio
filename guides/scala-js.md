# Build and Run a ZIO Application on Scala.js

> Write ZIO libraries and browser applications that target Scala.js: cross-compilation, the JS runtime model, zio-json persistence, DOM rendering, and a complete TodoMVC app.

## Introduction

By the end of this guide we will have two concrete results: a cross-compiled ZIO library that publishes both a JVM and a Scala.js artifact, and a complete, working browser application — a ZIO-powered TodoMVC — that persists to `localStorage`, supports hash-based routing between "All", "Active", and "Completed" views, and renders real DOM elements without a virtual DOM library. Every piece of the application half of this guide is drawn from a single working project so the code you read here is the code that actually runs in a browser, not a simplification of it.

This guide has no prerequisite Scala.js or browser JavaScript experience. If you already write ZIO effects on the JVM, everything platform-specific — the runtime model, DOM APIs, `localStorage`, hash routing — is introduced from first principles as we reach it.

## The Problem

A standard ZIO project today publishes only a JVM artifact. When a browser app team tries to consume it, the build fails immediately:

```scala
// mylib/build.sbt — published as JVM-only
lazy val mylib = project
  .in(file("mylib"))
  .settings(
    scalaVersion := "2.13.18",
    libraryDependencies += "dev.zio" %% "zio" % "2.1.26"
    // Publishes mylib_2.13.jar — no _sjs1 classifier, no Scala.js artifact
  )

// webapp/build.sbt — browser app that needs mylib's domain types
lazy val webapp = project
  .in(file("webapp"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaVersion := "2.13.18",
    libraryDependencies += "dev.zio" %%% "zio" % "2.1.26",
    // This line causes a hard resolution failure at `sbt update`:
    // [error] not found: com.example:mylib_sjs1_2.13:0.1.0
    libraryDependencies += "com.example" %%% "mylib" % "0.1.0"
  )
```

The `%%%` operator looks for `mylib_sjs1_2.13` but only `mylib_2.13` exists. The browser team has no path forward except to rewrite the same domain logic in TypeScript and maintain two independent implementations that drift every time the Scala model changes. A team writing a browser app from scratch faces a related but different problem: without a working example to start from, it is not obvious how a DOM event handler — an inherently side-effecting, callback-based API — should call into a ZIO effect at all.

## Prerequisites

Add the sbt Scala.js plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
```

If you are also cross-compiling a **library** for both the JVM and Scala.js (covered in [Configure a Cross-Compiled Library](#configure-a-cross-compiled-library) below), add the cross-project plugin too:

```scala
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")
```

A **browser application** — the TodoMVC app this guide builds — needs only `sbt-scalajs`. The next section explains why.

You need Node.js installed for `sbt test` — sbt-scalajs executes the compiled JavaScript through Node.js to run that task, even though the application itself later runs in a real browser. You do not need Node.js to open the finished app in a browser.

`sbt run` is not useful for the TodoMVC app this guide builds: `main()` calls straight into `dom.document.getElementById(...)` on startup, and Node.js has no `document` or `window` — the task fails immediately with a `ReferenceError`. This guide runs the app two ways instead: `sbt test` for the DOM-free unit tests, and `sbt fastLinkJS` followed by opening `index.html` in a real browser for the app itself.

The base imports shared across all code examples in this guide are:

```scala
import zio._
```

This guide assumes you already write ZIO effects and understand [`ZIOAppDefault`](../reference/core/zioapp.md). It assumes nothing about Scala.js, the DOM, or browser JavaScript.

## Libraries vs. Applications: Two Ways to Target Scala.js

Scala.js compiles Scala to JavaScript, but *what* you are compiling changes which sbt plugin setup you need:

| You are building...                                            | sbt setup                                     | Targets                       |
|------------------------------------------------------------------|------------------------------------------------|---------------------------------|
| A **library** consumed by both JVM and browser code               | `crossProject(JSPlatform, JVMPlatform)` + `sbt-scalajs-crossproject` | Two artifacts: `mylib_2.13` and `mylib_sjs1_2.13` |
| An **application** that only ever runs in the browser              | A plain `project` with `enablePlugins(ScalaJSPlugin)` | One artifact: JavaScript only  |

The TodoMVC app this guide builds is the second kind. It never runs on the JVM, so it has no reason to cross-compile — it is a normal sbt project that happens to target JavaScript instead of the JVM bytecode:

```scala
// build.sbt — a browser-only application, no crossProject
lazy val root = (project in file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "my-app",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "dev.zio" %%% "zio" % "2.1.26"
  )
```

`%%%` still appears here — it is provided by `sbt-scalajs` itself (not the cross-project plugin) and resolves any dependency to its Scala.js artifact. `scalaJSUseMainModuleInitializer := true` wraps the Scala `main()` call so that loading the compiled file in a `<script>` tag runs the app automatically; a library omits this setting because a library is consumed by another program, not run directly.

The rest of this guide covers both shapes: [Configure a Cross-Compiled Library](#configure-a-cross-compiled-library) for the library case, and [Build a Browser TodoMVC App](#build-a-browser-todomvc-app) for the application case.

## The Core Model

Both halves of this guide share one small domain model: a `Todo` and a `Filter` that determines which todos are visible.

```scala
import zio.json._

final case class Todo(id: String, title: String, completed: Boolean)

object Todo {
  implicit val codec: JsonCodec[Todo] = DeriveJsonCodec.gen[Todo]
}

sealed trait Filter
object Filter {
  case object All       extends Filter
  case object Active    extends Filter
  case object Completed extends Filter

  def apply(todos: List[Todo], f: Filter): List[Todo] = f match {
    case All       => todos
    case Active    => todos.filterNot(_.completed)
    case Completed => todos.filter(_.completed)
  }
}
```

`Todo.codec` is a `zio-json` `JsonCodec` derived once and reused everywhere the todo list needs to round-trip to text — we use it for `localStorage` persistence later in this guide. `Filter.apply` is the single pure function every view of the todo list passes through: the visible list is always `Filter(todos, currentFilter)`, never a separately maintained copy.

:::note[This Page's Scala Version vs. the Companion App's Scala Version]
The snippets on this page compile against this documentation site's own Scala 2.13 toolchain, so `Filter` is written here as a `sealed trait` with case objects — a shape that compiles unchanged under Scala 2 and Scala 3. The real companion application (embedded throughout this guide, and linked in full under [Running the Examples](#running-the-examples)) is written in Scala 3 and expresses the same idea as an `enum Filter derives CanEqual`. Both are the same model; only the surface syntax differs. `derives CanEqual` is a Scala 3 detail explained in [Wiring It Together](#wiring-it-together-main).
:::

## Configure a Cross-Compiled Library

Convert a single-platform `project` definition to a `crossProject` by importing the sbt cross-project DSL and replacing `project` with `crossProject(JSPlatform, JVMPlatform)`:

```scala
// build.sbt
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

lazy val mylib = crossProject(JSPlatform, JVMPlatform)
  .in(file("mylib"))
  .settings(
    scalaVersion := "2.13.18",
    // Use %%% (not %%) for all ZIO deps inside a crossProject
    libraryDependencies += "dev.zio" %%% "zio" % "2.1.26"
  )
  .jsSettings(
    // JS tests cannot run in a forked JVM process
    Test / fork := false,
    // Prevents IDE BSP confusion on the JS sub-project
    bspEnabled  := false
  )

lazy val mylibJS  = mylib.js
lazy val mylibJVM = mylib.jvm
```

`%%%` is provided by `sbt-scalajs-crossproject`. It appends the correct platform suffix at resolution time: `%%` on JVM, `_sjs1` on Scala.js. Every ZIO dependency inside a `crossProject` must use `%%%`.

After adding the plugins and converting the build, run the JS link step to confirm the output file is produced:

```text
$ sbt mylibJS/fastLinkJS
...
[success] Total time: 12 s
[info] Linked: target/scala-2.13/mylib-fastopt/main.js
```

## Write a Cross-Platform ZIO Program

Source files placed under `mylib/src/main/scala/` compile for both platforms without modification. An `object` that extends `ZIOAppDefault` works as a cross-platform entry point:

```scala
import zio._

object HelloApp extends ZIOAppDefault {
  def run: ZIO[Any, Any, Any] =
    Console.printLine("Hello from ZIO!") *> ZIO.never
}
```

On the JVM, `main()` blocks the calling thread until the effect completes. On Scala.js the JS-specific implementation calls `runtime.unsafe.fork`, which returns immediately after registering the fiber on the macrotask queue — `ZIO.never` keeps the fiber alive so the browser app does not shut down.

:::warning[Use `unsafe.fork`, Not `unsafe.run`, in the Browser]
`Runtime.unsafe.run` carries a documented caveat: "May fail on Scala.js if the effect cannot be entirely run synchronously." DOM callbacks are asynchronous by nature, so `unsafe.run` may fail silently or throw. Always use `runtime.unsafe.fork` when launching a ZIO effect from a DOM event handler or module initializer.
:::

The key runtime differences between JVM and Scala.js behavior apply to every program in this guide:

| Behaviour            | JVM                                          | Scala.js                                    |
|-----------------------|-----------------------------------------------|-----------------------------------------------|
| `main()` entry        | Blocks until effect completes                 | `runtime.unsafe.fork`, returns immediately    |
| `ZIO.blocking`        | Dedicated blocking thread pool                | Same executor as default (`MacrotaskExecutor`) |
| Process exit          | `System.exit(code)` terminates the JVM        | Sets `process.exitCode`; no-op in a browser   |
| `Runtime.unsafe.run`  | Blocks current thread until effect resolves   | May fail if effect cannot run synchronously   |
| Thread model          | Multiple OS threads, work-stealing scheduler  | Single JS thread, macrotask queue             |

`ZIO.blocking` compiles and runs on JS without change; it simply routes to the same executor rather than a separate pool.

## Build a Browser TodoMVC App

The rest of this guide builds one application: a TodoMVC implementation where ZIO owns every state transition and Scala.js owns every DOM interaction. It is six small files:

```text
src/main/scala/scalasjs/
├── Todo.scala        # the domain model (shown above)
├── Filter.scala      # the domain model (shown above)
├── Router.scala      # reads and reacts to the URL hash
├── TodoStore.scala   # Ref-backed state + localStorage persistence
├── View.scala        # builds real DOM elements for one todo item
└── Main.scala        # wires DOM events to the store and re-renders
```

### The App Project: `ScalaJSPlugin` Without `crossProject`

This app's `build.sbt` follows the "application" shape from [Libraries vs. Applications](#libraries-vs-applications-two-ways-to-target-scalajs) above — one `project`, `ScalaJSPlugin` enabled directly, no cross-project plugin:

```scala
ThisBuild / scalaVersion := "3.3.8"

lazy val root = (project in file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "zio-todomvc",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "app",
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "app",
    libraryDependencies ++= Seq(
      "dev.zio"      %%% "zio"          % "2.1.26",
      "dev.zio"      %%% "zio-json"     % "0.7.38",
      "org.scala-js" %%% "scalajs-dom"  % "2.8.1",
      "dev.zio"      %%% "zio-test"     % "2.1.26" % Test,
      "dev.zio"      %%% "zio-test-sbt" % "2.1.26" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
```

Three settings are new here, all specific to browser apps:

- **`ModuleKind.NoModule`** — the linker emits one plain script with no `import`/`export` statements, so it loads with a bare `<script src="app/main.js"></script>` tag and works even when the page is opened directly from disk (`file://`), with no bundler and no static file server required.
- **`scalaJSLinkerOutputDirectory := baseDirectory.value / "app"`** — puts the linked JavaScript next to `index.html` instead of buried under `target/`, so the `<script>` tag's relative path stays simple.
- **`org.scala-js %%% scalajs-dom`** — typed Scala bindings for the browser DOM API. Every file below that touches an element, an event, or `localStorage` imports `org.scalajs.dom`.

### State: `TodoStore` and Two `Ref`s

`TodoStore` holds all mutable state behind two `Ref`s — one for the todo list, one for the current filter — and exposes every mutation as a `UIO[Unit]`:

```scala
// zio-examples/scala-js/src/main/scala/scalasjs/TodoStore.scala (excerpt)
final class TodoStore(todosRef: Ref[List[Todo]], filterRef: Ref[Filter]):

  def all: UIO[List[Todo]] = todosRef.get

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
```

Every method returns `UIO[Unit]`, not `Task[Unit]`: there is no failure mode for adding a todo to an in-memory list, so the error channel is `Nothing`. `add` and `toggle` both end with `*> persist` — every mutation immediately writes the new list to `localStorage` before the effect completes, so the UI and the storage layer can never observe different states. The full file — including `edit`, `delete`, `toggleAll`, and `clearCompleted`, all following the same one-line-mutation-plus-persist pattern — is embedded in full under [Running the Examples](#running-the-examples).

### Persistence: `zio-json` and `localStorage`

`Todo.codec`, derived once in [The Core Model](#the-core-model), is all `zio-json` needs to serialize the todo list to a string:

```scala
import zio.json._

val todo = Todo("1", "Buy milk", completed = false)
// todo: Todo = Todo(id = "1", title = "Buy milk", completed = false)
todo.toJson
// res1: String = "{\"id\":\"1\",\"title\":\"Buy milk\",\"completed\":false}"
```

`TodoStore` uses exactly this — `.toJson` and `.fromJson[List[Todo]]` — to bridge between the in-memory `List[Todo]` and the browser's `localStorage`, which only stores strings:

```scala
// zio-examples/scala-js/src/main/scala/scalasjs/TodoStore.scala (excerpt)
private def persist: UIO[Unit] =
  todosRef.get.map { todos =>
    dom.window.localStorage.setItem(TodoStore.storageKey, todos.toJson)
  }

private def loadFromStorage(): List[Todo] =
  Option(dom.window.localStorage.getItem(storageKey)) match
    case Some(raw) => raw.fromJson[List[Todo]].getOrElse(Nil)
    case None      => Nil
```

`fromJson` returns an `Either[String, List[Todo]]`; `getOrElse(Nil)` treats a missing key or a corrupted stored value the same way — an empty list — rather than crashing the app on startup. `localStorage.setItem`/`getItem` are synchronous browser APIs, so both methods run as plain (non-ZIO) code and are lifted into the effect system at their call sites — `persist` inside `todosRef.get.map`, `loadFromStorage` inside the `ZIO.succeed` you will see in `TodoStore.make` in the full embedded file.

### Routing: Hash-Based Navigation

TodoMVC's three views — All, Active, Completed — are selected by the URL fragment (`#/`, `#/active`, `#/completed`), not by a server route. `Router` reads the current hash and re-invokes a callback whenever it changes:

```scala
// zio-examples/scala-js/src/main/scala/scalasjs/Router.scala (complete file)
object Router:

  def current: Filter = Filter.fromHash(dom.window.location.hash)

  def onChange(handler: Filter => Unit): Unit =
    dom.window.onhashchange = (_: dom.Event) => handler(current)
```

Hash routing needs no server-side support at all — the browser never sends the fragment in an HTTP request — which is why it is the standard choice for a purely client-side app like this one. `Filter.fromHash` and `Filter.hash` — the two conversions between a URL fragment and a `Filter` value — are part of the full `Filter.scala` embedded under [Running the Examples](#running-the-examples).

### Rendering: Real DOM Elements, Not Strings

A common first instinct is to render by building an HTML string and assigning it to `container.innerHTML`. That approach silently destroys any element the browser is tracking internally — most importantly, a todo item mid-edit loses focus and its cursor position on every re-render. `View.renderTodoItem` instead builds real `dom.Element` nodes with `document.createElement`, the same way the browser's own APIs expect:

```scala
// zio-examples/scala-js/src/main/scala/scalasjs/View.scala (excerpt)
def renderTodoItem(
    todo: Todo,
    onToggle: String => Unit,
    onDestroy: String => Unit,
    onCommitEdit: (String, String) => Unit
): html.LI =
  val li = dom.document.createElement("li").asInstanceOf[html.LI]
  li.className = if todo.completed then "completed" else ""

  val toggle = dom.document.createElement("input").asInstanceOf[html.Input]
  toggle.`type` = "checkbox"
  toggle.checked = todo.completed
  toggle.onclick = (_: dom.Event) => onToggle(todo.id)

  val label = dom.document.createElement("label").asInstanceOf[html.Label]
  label.textContent = todo.title

  // ... destroy button, edit input, and the double-click-to-edit state
  // machine follow the same createElement-and-wire pattern — see the
  // full file below.
  li
```

`renderTodoItem` never calls into ZIO itself. It takes three plain callbacks — `onToggle`, `onDestroy`, `onCommitEdit` — as `String => Unit` functions, and the caller (`Main`, next) is the only place those callbacks are connected to ZIO effects. This keeps `View` a pure DOM-building function: given a `Todo` and three callbacks, it always produces the same element tree, which makes it easy to reason about independent of the ZIO runtime.

### Wiring It Together: `Main`

`Main` is the one place a DOM callback — synchronous, `Unit`-returning, and outside ZIO's control — is bridged into a ZIO effect:

```scala
// zio-examples/scala-js/src/main/scala/scalasjs/Main.scala (excerpt)
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
```

`runFireAndForget` is the bridge used everywhere in this app that a DOM event needs to trigger a ZIO effect — every `onclick`, every `onkeydown`, and the top-level `Router.onChange` callback all call it. It matches the `unsafe.fork`-not-`unsafe.run` rule from [Write a Cross-Platform ZIO Program](#write-a-cross-platform-zio-program) exactly: DOM callbacks are synchronous `Unit`-returning functions, so the effect inside must be forked, not run to completion, at the call site.

`wire` attaches every top-level listener once, at startup; `rerender` — called after every single mutation — reads the current state back out of `TodoStore` and repaints the whole list:

```scala
// zio-examples/scala-js/src/main/scala/scalasjs/Main.scala (excerpt)
private def rerender(store: TodoStore): UIO[Unit] =
  for
    all     <- store.all
    visible <- store.visible
    f       <- store.filter
    _       <- ZIO.succeed(paint(store, all, visible, f))
  yield ()
```

There is no diffing and no virtual DOM here: `paint` clears `todoList` and rebuilds every visible `<li>` from scratch on every state change. At TodoMVC's scale this is simple, correct, and fast enough that the cost is invisible to a user. A larger application would want to diff against the previous render instead of rebuilding everything — that technique is outside this guide's scope, but the `Ref`-in, `UIO[Unit]`-out shape of every `TodoStore` method stays the same regardless of how rendering is eventually optimized.

One Scala 3 detail surfaces in the real `Filter` type used by `paint`'s filter-highlighting logic: because `Filter` is declared as `enum Filter derives CanEqual`, comparing two `Filter` values with `==` type-checks under Scala 3's multiversal equality — without `derives CanEqual`, comparing values of an unrelated type to `Filter` would be a compile error by design, and comparing two `Filter`s would need an explicit `(f1: Filter) == (f2: Filter)` type ascription to satisfy the compiler.

### Running the App in a Browser

Link the app and open it:

```bash
sbt fastLinkJS
```

`fastLinkJS` writes `app/main.js` next to `index.html` (per the `scalaJSLinkerOutputDirectory` setting above). Open `index.html` directly in a browser — no server needed, because `ModuleKind.NoModule` produces a plain script, not an ES module subject to the browser's same-origin restrictions on `file://` fetches. Add a few todos, reload the page, and confirm they are still there: that round trip exercises `TodoStore.make`'s `loadFromStorage` and every mutation's `persist` call.

For a production build, use `fullLinkJS` instead — it runs the Scala.js optimizer's dead-code elimination and produces a substantially smaller `main.js`:

```bash
sbt fullLinkJS
```

## Run Tests on JS

`zio-test` specs compile and run on Scala.js unchanged, and — unlike the app itself — they run under Node.js, not a browser, so a test that never touches the DOM needs nothing extra. This app's own test suite is exactly that: two specs that test pure logic, with no `org.scalajs.dom` import in either file.

`FilterSpec` tests `Filter.apply` and `Filter.fromHash` directly:

```scala
import zio.test._

object FilterSpec extends ZIOSpecDefault {
  private val todos = List(
    Todo("1", "buy milk", completed = false),
    Todo("2", "walk dog", completed = true)
  )

  def spec = suite("Filter")(
    test("Active returns only incomplete todos") {
      assertTrue(Filter(todos, Filter.Active) == List(todos(0)))
    }
  )
}
```

`TodoJsonSpec` tests that `Todo.codec` round-trips correctly, including a title containing non-ASCII characters — the kind of input that silently breaks a hand-rolled JSON encoder but is exactly what `zio-json`'s derived codec is built to handle correctly. Both full specs are embedded under [Running the Examples](#running-the-examples).

For library code that needs to behave differently across platforms — rather than an application's own tests — use `TestAspect.jsOnly` to gate a test so it only runs when the suite executes on Scala.js:

```scala
import zio._
import zio.test._
import zio.test.TestAspect.jsOnly

object PlatformSpec extends ZIOSpecDefault {
  def spec = suite("PlatformSpec")(
    test("exercises JS-specific behavior") {
      assertTrue(true)
    } @@ jsOnly
  )
}
```

`zio-test-magnolia` and `zio-test-refined` are available on JS (but not on Native). `zio-test-junit` is JVM-only and cannot be added to a JS sub-project's dependencies.

## Cross-Publish JVM and JS Artifacts

To publish both sub-projects of a cross-compiled **library** from one `sbt publish` command, aggregate them under a root project and mark the root itself as unpublished:

```scala
// build.sbt — root aggregate
lazy val root = project
  .in(file("."))
  .aggregate(mylibJS, mylibJVM)
  .settings(publish / skip := true)
```

`publish / skip := true` on the root prevents sbt from emitting a spurious empty root artifact alongside the real ones. The aggregated sub-projects — `mylibJS` and `mylibJVM` — are still published when you run `sbt +publishLocal`.

After publishing locally with `sbt +publishLocal`, both artifacts appear in `~/.ivy2/local/` with platform classifiers:

```text
$ sbt +publishLocal
...
[info] published mylib_2.13 to ~/.ivy2/local/com.example/mylib_2.13/0.1.0-SNAPSHOT/...
[info] published mylib_sjs1_2.13 to ~/.ivy2/local/com.example/mylib_sjs1_2.13/0.1.0-SNAPSHOT/...
```

Consumers write `"com.example" %%% "mylib" % "<version>"` in their own `crossProject` and sbt resolves the `_sjs1_2.13` artifact automatically for the JS sub-project. A browser-only **application** — like the TodoMVC app in this guide — is never published this way; it is linked with `fullLinkJS` and shipped as a JavaScript bundle instead.

## Putting It Together

`Main` is the file that ties every other piece of the app — `TodoStore`, `Router`, `View` — into a running program:

```scala title="zio-examples/scala-js/src/main/scala/scalasjs/Main.scala" showLineNumbers
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
```

## Running the Examples

Every file this guide referenced by excerpt is embedded here in full, exactly as it compiles and runs in `zio-examples/scala-js/`.

### The Domain Model

<details>
<summary>Todo.scala — source with line numbers</summary>

```scala title="zio-examples/scala-js/src/main/scala/scalasjs/Todo.scala" showLineNumbers
package scalasjs

import zio.json.*

final case class Todo(id: String, title: String, completed: Boolean)

object Todo:
  given JsonCodec[Todo] = DeriveJsonCodec.gen[Todo]
```

</details>

<details>
<summary>Filter.scala — source with line numbers</summary>

```scala title="zio-examples/scala-js/src/main/scala/scalasjs/Filter.scala" showLineNumbers
package scalasjs

enum Filter derives CanEqual:
  case All, Active, Completed

object Filter:
  def fromHash(hash: String): Filter = hash match
    case "#/active"    => Filter.Active
    case "#/completed" => Filter.Completed
    case _             => Filter.All

  def hash(f: Filter): String = f match
    case Filter.All       => "#/"
    case Filter.Active    => "#/active"
    case Filter.Completed => "#/completed"

  def apply(todos: List[Todo], f: Filter): List[Todo] = f match
    case Filter.All       => todos
    case Filter.Active    => todos.filterNot(_.completed)
    case Filter.Completed => todos.filter(_.completed)
```

</details>

### State, Routing, and Rendering

<details>
<summary>TodoStore.scala — source with line numbers</summary>

```scala title="zio-examples/scala-js/src/main/scala/scalasjs/TodoStore.scala" showLineNumbers
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
```

</details>

<details>
<summary>Router.scala — source with line numbers</summary>

```scala title="zio-examples/scala-js/src/main/scala/scalasjs/Router.scala" showLineNumbers
package scalasjs

import org.scalajs.dom

object Router:

  def current: Filter = Filter.fromHash(dom.window.location.hash)

  def onChange(handler: Filter => Unit): Unit =
    dom.window.onhashchange = (_: dom.Event) => handler(current)
```

</details>

<details>
<summary>View.scala — source with line numbers</summary>

```scala title="zio-examples/scala-js/src/main/scala/scalasjs/View.scala" showLineNumbers
package scalasjs

import org.scalajs.dom
import org.scalajs.dom.html

object View:

  def renderItemCount(activeCount: Int): String =
    val noun = if activeCount == 1 then "item" else "items"
    s"$activeCount $noun left"

  def renderTodoItem(
      todo: Todo,
      onToggle: String => Unit,
      onDestroy: String => Unit,
      onCommitEdit: (String, String) => Unit
  ): html.LI =
    val li = dom.document.createElement("li").asInstanceOf[html.LI]
    li.className = if todo.completed then "completed" else ""

    val view = dom.document.createElement("div").asInstanceOf[html.Div]
    view.className = "view"

    val toggle = dom.document.createElement("input").asInstanceOf[html.Input]
    toggle.`type` = "checkbox"
    toggle.className = "toggle"
    toggle.checked = todo.completed
    toggle.onclick = (_: dom.Event) => onToggle(todo.id)

    val label = dom.document.createElement("label").asInstanceOf[html.Label]
    label.textContent = todo.title

    val destroy = dom.document.createElement("button").asInstanceOf[html.Button]
    destroy.className = "destroy"
    destroy.onclick = (_: dom.Event) => onDestroy(todo.id)

    view.appendChild(toggle)
    view.appendChild(label)
    view.appendChild(destroy)
    li.appendChild(view)

    val edit = dom.document.createElement("input").asInstanceOf[html.Input]
    edit.className = "edit"
    edit.value = todo.title
    li.appendChild(edit)

    def enterEditMode(): Unit =
      li.classList.add("editing")
      edit.value = todo.title
      edit.focus()

    def exitEditMode(): Unit =
      li.classList.remove("editing")

    def commit(): Unit =
      exitEditMode()
      onCommitEdit(todo.id, edit.value)

    label.ondblclick = (_: dom.Event) => enterEditMode()

    edit.onkeydown = (e: dom.KeyboardEvent) =>
      if e.key == "Enter" then commit()
      else if e.key == "Escape" then
        edit.value = todo.title
        exitEditMode()

    edit.onblur = (_: dom.Event) => if li.classList.contains("editing") then commit()

    li
```

</details>

Run the app itself with `sbt fastLinkJS` and open `index.html`, as shown in [Running the App in a Browser](#running-the-app-in-a-browser).

### Tests

<details>
<summary>FilterSpec.scala — source with line numbers</summary>

```scala title="zio-examples/scala-js/src/test/scala/scalasjs/FilterSpec.scala" showLineNumbers
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
```

</details>

<details>
<summary>TodoJsonSpec.scala — source with line numbers</summary>

```scala title="zio-examples/scala-js/src/test/scala/scalasjs/TodoJsonSpec.scala" showLineNumbers
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
```

</details>

Run both specs with:

```bash
sbt test
```

## Going Further

The [`ZIOAppDefault`](../reference/core/zioapp.md) reference page covers the full lifecycle — environment, error channel, and graceful shutdown — that applies equally on JVM and JS. The [`Runtime`](../reference/core/runtime.md) reference documents every `unsafe` method, including the `fork` vs `run` distinction that matters most on Scala.js. The [`Ref`](../reference/concurrency/ref.md) reference covers the full `Ref` API — `modify`, `updateAndGet`, `getAndUpdate`, and `Ref.Synchronized` — all of which compile and run on JS unchanged.

For interoperability between ZIO and native browser JavaScript, see [Interop with JavaScript](./interop/with-javascript.md), which covers `ZIO.fromPromiseJS`, `toPromiseJS`, and patterns for bridging the `scala.scalajs.js.Promise` type. For parallel cross-compilation targeting native binaries without the JVM, see the [Scala Native guide](./scala-native.md), which uses an identical `crossProject` setup.

The ZIO modules that publish a Scala.js artifact are:

| Module                 | JS Support | Notes                                                        |
|-------------------------|:----------:|----------------------------------------------------------------|
| `zio` core              |     ✅      | Full support                                                   |
| `zio-streams`           |     ✅      | Full support                                                   |
| `zio-json`              |     ✅      | Full support — used for `localStorage` persistence in this guide |
| `zio-test`              |     ✅      | Full support                                                   |
| `zio-test-sbt`          |     ✅      | Full support                                                   |
| `zio-test-magnolia`     |     ✅      | JS + JVM only, no Native                                        |
| `zio-test-refined`      |     ✅      | JS + JVM only, no Native                                        |
| `zio-test-scalacheck`   |     ✅      | Full support                                                   |
| `zio-concurrent`        |     ✅      | Full support                                                   |
| `zio-test-junit`        |     ❌      | JVM-only                                                        |

When you are ready to ship to production, replace `fastLinkJS` with `fullLinkJS` in your CI pipeline — `fullLinkJS` enables dead-code elimination and produces a significantly smaller output bundle. For bundling and tree-shaking, pair `fullLinkJS` with Vite or webpack using the output file from `target/scala-3.3.8/<project>-opt/main.js` as the entry point.
