# Homepage Code Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a tabbed "Show me the code" section with five compile-checked ZIO snippets to the zio.dev homepage, between Features and Ecosystem.

**Architecture:** New `CodeShowcase` section component following the existing section pattern (`index.jsx` + `data.js` under `website/src/components/sections/`). Rendering is Docusaurus-native: `@theme/Tabs` + `@theme/TabItem` + `@theme/CodeBlock`. Snippets live as template literals in `data.js` and are compile-verified by a committed `scala-cli` scratch file.

**Tech Stack:** Docusaurus 3.7 (React JSX), Tailwind v4 utilities, scala-cli ≥1.5 with ZIO 2.1.26.

**Spec:** `specs/2026-07-17-homepage-code-showcase-design.md`

## Global Constraints

- ZIO version for compile checks: `2.1.26` (zio + zio-streams).
- Snippets shown on the page omit import lines; the scratch file restores them.
- The five tabs, in order: Concurrency, Error handling, Resource safety, Streaming, Dependency Injection.
- Run `npx prettier --write` (from `website/`) on every JS/JSX file before committing it.
- Plans/specs live in root `specs/`, never in `docs/` (the `docs/` tree is published to zio.dev by the mdoc pipeline).
- Commit messages: Conventional Commits, no AI attribution trailers.
- All work happens on branch `home-page-harmony`.

---

### Task 1: Compile-checked snippets + data module

**Files:**
- Create: `specs/snippet-check/showcase.scala`
- Create: `website/src/components/sections/CodeShowcase/data.js`

**Interfaces:**
- Produces: `examples` — array of `{ value: string, label: string, takeaway: string, code: string }`, imported by Task 2 as `import { examples } from './data';`

- [ ] **Step 1: Write the compile-check scratch file**

Create `specs/snippet-check/showcase.scala`. The five `SnippetN` objects contain the exact code that will appear on the homepage (plus imports and stub definitions that the page omits for signal density). If a snippet in `data.js` ever changes, this file must change with it.

```scala
//> using scala 3.5.1
//> using dep dev.zio::zio:2.1.26
//> using dep dev.zio::zio-streams:2.1.26

// Compile check for the homepage CodeShowcase snippets
// (website/src/components/sections/CodeShowcase/data.js).
// Run: scala-cli compile specs/snippet-check/showcase.scala

import zio.*
import zio.stream.*
import java.io.IOException

// ── Stubs standing in for "your code" in the homepage snippets ──────────
case class User(name: String)
case class Config()
object Config { val fallback: Config = Config() }
case class Stats()
case class Event(isValid: Boolean)
class File

class Database { def insert(name: String): Task[User] = ZIO.succeed(User(name)) }
object Database {
  val connect: ZIO[Scope, Throwable, Database] = ZIO.succeed(new Database)
  val live: ULayer[Database]                   = ZLayer.succeed(new Database)
}

class Logger { def info(msg: String): UIO[Unit] = ZIO.unit }
object Logger { val live: ULayer[Logger] = ZLayer.succeed(new Logger) }

def fetchUsers: Task[List[User]]        = ZIO.succeed(Nil)
def fetchOrders: Task[List[String]]     = ZIO.succeed(Nil)
def fetchProfile(id: Int): Task[User]   = ZIO.succeed(User(id.toString))
val userIds: List[Int]                  = List(1, 2, 3)

sealed trait AppError
case class NetworkError(msg: String) extends AppError
case class ParseError(line: Int)     extends AppError

val fetchConfig: ZIO[Any, AppError, Config] = ZIO.succeed(Config())
val cachedConfig: UIO[Config]               = ZIO.succeed(Config())

def openFile(path: String): IO[IOException, File]  = ZIO.succeed(new File)
def closeFile(f: File): UIO[Unit]                  = ZIO.unit
def computeStats(f: File): IO[IOException, Stats]  = ZIO.succeed(Stats())
def logFile(path: String): ZIO[Scope, Throwable, File] = ZIO.succeed(new File)
def runMigrations(db: Database, f: File): Task[Unit]   = ZIO.unit

val events: List[Event]                    = List(Event(true))
def enrich(e: Event): Task[Event]          = ZIO.succeed(e)
def writeBatch(c: Chunk[Event]): Task[Unit] = ZIO.unit

// ── Snippet 1: Concurrency ──────────────────────────────────────────────
object Snippet1 {
  val users  = fetchUsers.retry(Schedule.recurs(3))
  val orders = fetchOrders.timeout(2.seconds)

  // Run both in parallel; if one fails, the other is interrupted
  val both = users.zipPar(orders)

  // Or a whole collection at once
  val profiles = ZIO.foreachPar(userIds)(fetchProfile)
}

// ── Snippet 2: Error handling ───────────────────────────────────────────
object Snippet2 {
  val program: ZIO[Any, Nothing, Config] =
    fetchConfig
      .retry(Schedule.exponential(100.millis) && Schedule.recurs(5))
      .catchAll {
        case NetworkError(_) => cachedConfig
        case ParseError(_)   => ZIO.succeed(Config.fallback)
      }
}

// ── Snippet 3: Resource safety ──────────────────────────────────────────
object Snippet3 {
  def analyze(path: String): ZIO[Any, IOException, Stats] =
    ZIO.acquireReleaseWith(openFile(path))(closeFile) { file =>
      computeStats(file)
    }

  // Or compose many resources with Scope
  val app: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        db   <- Database.connect
        file <- logFile("app.log")
        _    <- runMigrations(db, file)
      } yield ()
    } // released in reverse order — even on failure or interruption
}

// ── Snippet 4: Streaming ────────────────────────────────────────────────
object Snippet4 {
  val pipeline: ZIO[Any, Throwable, Unit] =
    ZStream
      .fromIterable(events)          // or Kafka, files, sockets…
      .mapZIOPar(20)(enrich)         // 20 concurrent enrichments
      .filter(_.isValid)
      .grouped(100)                  // batch for the database
      .mapZIO(writeBatch)
      .runDrain
}

// ── Snippet 5: Dependency Injection ─────────────────────────────────────
object Snippet5 {
  class UserService(db: Database, logger: Logger) {
    def signup(name: String): Task[User] =
      logger.info(s"signing up $name") *> db.insert(name)
  }

  object UserService {
    val live: ZLayer[Database & Logger, Nothing, UserService] =
      ZLayer.fromFunction(new UserService(_, _))
  }

  val app: ZIO[UserService, Throwable, User] =
    ZIO.serviceWithZIO[UserService](_.signup("Ada"))

  // Compile-time-checked wiring: forget a layer and the build fails
  val runnable = app.provide(UserService.live, Database.live, Logger.live)
}
```

- [ ] **Step 2: Run the compile check**

Run: `scala-cli compile specs/snippet-check/showcase.scala`
Expected: compiles with no errors (warnings about unused values are fine). If an API doesn't compile, fix the snippet here first — this file is the source of truth for what goes into `data.js`.

- [ ] **Step 3: Write the data module**

Create `website/src/components/sections/CodeShowcase/data.js`. The `code` strings are the `SnippetN` object bodies from Step 1, dedented, without the stub/import scaffolding:

```js
// Each snippet is compile-checked by specs/snippet-check/showcase.scala.
// If you edit a snippet here, update that file and re-run:
//   scala-cli compile specs/snippet-check/showcase.scala
export const examples = [
  {
    value: 'concurrency',
    label: 'Concurrency',
    takeaway:
      'Fibers, not threads — parallelism is one combinator, and interruption is handled for you.',
    code: `val users  = fetchUsers.retry(Schedule.recurs(3))
val orders = fetchOrders.timeout(2.seconds)

// Run both in parallel; if one fails, the other is interrupted
val both = users.zipPar(orders)

// Or a whole collection at once
val profiles = ZIO.foreachPar(userIds)(fetchProfile)`,
  },
  {
    value: 'errors',
    label: 'Error handling',
    takeaway:
      'Errors are typed and visible in the signature — the compiler knows what can fail and when you have handled it all.',
    code: `val program: ZIO[Any, Nothing, Config] =
  fetchConfig
    .retry(Schedule.exponential(100.millis) && Schedule.recurs(5))
    .catchAll {
      case NetworkError(_) => cachedConfig
      case ParseError(_)   => ZIO.succeed(Config.fallback)
    }`,
  },
  {
    value: 'resources',
    label: 'Resource safety',
    takeaway:
      'Acquire and release are paired at the type level — leaks are impossible, even under interruption.',
    code: `def analyze(path: String): ZIO[Any, IOException, Stats] =
  ZIO.acquireReleaseWith(openFile(path))(closeFile) { file =>
    computeStats(file)
  }

// Or compose many resources with Scope
val app: ZIO[Any, Throwable, Unit] =
  ZIO.scoped {
    for {
      db   <- Database.connect
      file <- logFile("app.log")
      _    <- runMigrations(db, file)
    } yield ()
  } // released in reverse order — even on failure or interruption`,
  },
  {
    value: 'streaming',
    label: 'Streaming',
    takeaway:
      'Infinite data, finite memory — backpressure and concurrency built into every stage.',
    code: `val pipeline: ZIO[Any, Throwable, Unit] =
  ZStream
    .fromIterable(events)          // or Kafka, files, sockets…
    .mapZIOPar(20)(enrich)         // 20 concurrent enrichments
    .filter(_.isValid)
    .grouped(100)                  // batch for the database
    .mapZIO(writeBatch)
    .runDrain`,
  },
  {
    value: 'di',
    label: 'Dependency Injection',
    takeaway:
      'Wiring is checked at compile time — forget a dependency and the build fails, not production.',
    code: `class UserService(db: Database, logger: Logger) {
  def signup(name: String): Task[User] =
    logger.info(s"signing up $name") *> db.insert(name)
}

object UserService {
  val live: ZLayer[Database & Logger, Nothing, UserService] =
    ZLayer.fromFunction(new UserService(_, _))
}

val app: ZIO[UserService, Throwable, User] =
  ZIO.serviceWithZIO[UserService](_.signup("Ada"))

// Compile-time-checked wiring: forget a layer and the build fails
val runnable = app.provide(UserService.live, Database.live, Logger.live)`,
  },
];
```

Note on the DI snippet: JS template literals only treat `${` specially, so the Scala interpolator `$name` (no brace) needs no escaping. Verify the rendered page shows `s"signing up $name"`.

- [ ] **Step 4: Format**

Run from `website/`: `npx prettier --write src/components/sections/CodeShowcase/data.js`
Expected: file listed as formatted.

- [ ] **Step 5: Commit**

```bash
git add specs/snippet-check/showcase.scala website/src/components/sections/CodeShowcase/data.js
git commit -m "feat(website): add compile-checked snippets for homepage code showcase"
```

---

### Task 2: CodeShowcase component + homepage mount

**Files:**
- Create: `website/src/components/sections/CodeShowcase/index.jsx`
- Modify: `website/src/pages/index.jsx` (imports block and the `<main>` block between `<Features />` and `<Ecosystem …/>`)

**Interfaces:**
- Consumes: `examples` from `./data` (Task 1): `{ value, label, takeaway, code }[]`
- Produces: default export `CodeShowcase` React component, mounted by `pages/index.jsx`

- [ ] **Step 1: Write the component**

Create `website/src/components/sections/CodeShowcase/index.jsx`:

```jsx
import React from 'react';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import CodeBlock from '@theme/CodeBlock';

import SectionWrapper from '@site/src/components/ui/SectionWrapper';

import { examples } from './data';

export default function CodeShowcase() {
  return (
    <SectionWrapper
      title="Show me the code"
      subtitle="Five everyday problems, solved the ZIO way"
    >
      <div className="container">
        <div className="mx-auto max-w-3xl">
          <Tabs>
            {examples.map((example) => (
              <TabItem
                key={example.value}
                value={example.value}
                label={example.label}
              >
                <CodeBlock language="scala">{example.code}</CodeBlock>
                <p className="text-zinc-600 dark:text-zinc-400">
                  {example.takeaway}
                </p>
              </TabItem>
            ))}
          </Tabs>
        </div>
      </div>
    </SectionWrapper>
  );
}
```

- [ ] **Step 2: Mount it on the homepage**

In `website/src/pages/index.jsx`, add the import after the `Features` import:

```jsx
import CodeShowcase from '@site/src/components/sections/CodeShowcase';
```

and add the section between the existing `Features` and `Ecosystem` `Reveal` blocks:

```jsx
        <Reveal>
          <Features />
        </Reveal>
        <Reveal>
          <CodeShowcase />
        </Reveal>
        <Reveal>
          <Ecosystem
```

- [ ] **Step 3: Format**

Run from `website/`: `npx prettier --write src/components/sections/CodeShowcase/index.jsx src/pages/index.jsx`
Expected: both files listed as formatted.

- [ ] **Step 4: Verify with a production build**

Run from `website/`: `npm run build` (several minutes)
Expected: build succeeds. Then verify the SSR output:

```bash
grep -c "Show me the code" build/index.html    # expected: ≥1
grep -c "zipPar" build/index.html              # expected: ≥1
grep -cF 'signing up $name' build/index.html   # expected: ≥1 (single quotes: keep $name out of bash)
```

- [ ] **Step 5: Browser check**

With the dev server running (`localhost:3000`), verify on the homepage:
- Section appears between Features and Ecosystem with the standard title + gradient rule.
- Five tabs in spec order; clicking each shows a highlighted Scala snippet + takeaway line.
- Arrow-key navigation moves between tabs (Docusaurus `Tabs` built-in).
- Toggle dark mode: code block and takeaway text recolor correctly.

- [ ] **Step 6: Commit**

```bash
git add website/src/components/sections/CodeShowcase/index.jsx website/src/pages/index.jsx
git commit -m "feat(website): add tabbed code showcase section to homepage"
```
