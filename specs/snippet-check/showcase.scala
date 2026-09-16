//> using scala 3.9.0
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
case class Report()
case class Event(id: Int)
class File { def close(): Unit = () }

class Database { def insert(name: String): Task[User] = ZIO.succeed(User(name)) }
object Database {
  val connect: ZIO[Scope, Throwable, Database] = ZIO.succeed(new Database)
  val live: ULayer[Database]                   = ZLayer.succeed(new Database)
}

class Logger { def info(msg: String): UIO[Unit] = ZIO.unit }
object Logger { val live: ULayer[Logger] = ZLayer.succeed(new Logger) }

// The Snippet 5 example trims the service class bodies (they are implied), so
// the classes it wires up live here with the rest of "your code". Both take a
// Database, which is what makes the sharing in that snippet meaningful.
class UserService(db: Database, logger: Logger) {
  def signup(name: String): Task[User] =
    logger.info(s"signing up $name") *> db.insert(name)
}

class AuditService(db: Database) {
  def record(name: String): UIO[Unit] = ZIO.unit
}

val app: ZIO[UserService & AuditService, Throwable, User] =
  ZIO.serviceWithZIO[UserService](_.signup("John"))

def runFast(name: String): Task[String] = ZIO.succeed(name)
def attemptParallelPark(): IO[String, String] = ZIO.succeed("parked")

def logFile(path: String): ZIO[Scope, Throwable, File] = ZIO.succeed(new File)
def runMigrations(db: Database, f: File): Task[Unit]   = ZIO.unit

class DbConn { def close(): Unit = () }
class CacheConn { def flush(): Unit = () }
def connectDatabase(): Task[DbConn] = ZIO.succeed(new DbConn)
def connectCache(): Task[CacheConn] = ZIO.succeed(new CacheConn)
def openLogFile(): IO[IOException, File] = ZIO.succeed(new File)
def doWork(db: DbConn, cache: CacheConn, logger: File): Task[Report] = ZIO.succeed(Report())

val events: List[Event]                    = List(Event(1))
def enrich(e: Event): Task[Event]          = ZIO.succeed(e)
def write(e: Event): Task[Unit]            = ZIO.unit

// ── Snippet 1: Concurrency ──────────────────────────────────────────────
// Matches the "ZIO.race" example mounted in the Concurrency tab's Visual
// view (website/src/components/visual-effects/scenarios/RaceVisual.jsx) —
// Visual and Code must show the same example.
object Snippet1 {
  val tortoise = runFast("tortoise")
  val achilles = runFast("achilles")

  val winner = tortoise.race(achilles)
}

// ── Snippet 2: Error handling ───────────────────────────────────────────
// Matches the "ZIO.retry" example mounted in the Error handling tab's
// Visual view (website/src/components/visual-effects/scenarios/RetryExponentialVisual.jsx)
// — Visual and Code must show the same example.
object Snippet2 {
  val park   = attemptParallelPark()
  val result = park.retry(Schedule.exponential(700.millis))
}

// ── Snippet 3: Resource safety ──────────────────────────────────────────
// Matches the "ZIO.acquireRelease" example mounted in the Resource safety
// tab's Visual view (website/src/components/visual-effects/scenarios/AcquireReleaseVisual.jsx)
// — Visual and Code must show the same example.
object Snippet3 {
  val result: ZIO[Any, Throwable, Report] =
    ZIO.scoped:
      for
        db     <- ZIO.acquireRelease(connectDatabase())(db => ZIO.succeedBlocking(db.close()))
        cache  <- ZIO.acquireRelease(connectCache())(cache => ZIO.succeedBlocking(cache.flush()))
        logger <- ZIO.acquireRelease(openLogFile())(file => ZIO.succeedBlocking(file.close()))
        r      <- doWork(db, cache, logger)
      yield r
}

// ── Snippet 4: Streaming ────────────────────────────────────────────────
object Snippet4 {
  val pipeline: ZIO[Any, Throwable, Unit] =
    ZStream
      .fromIterable(events) // or Kafka, files, sockets…
      .mapZIOPar(4)(enrich) // 4 concurrent enrichments
      .buffer(2)            // bounded — fills when the sink lags
      .mapZIOPar(2)(write)  // 2 writers — still the bottleneck
      .runDrain
}

// ── Snippet 5: Dependency Injection ─────────────────────────────────────
object Snippet5 {
  object UserService:
    val live: ZLayer[Database & Logger, Nothing, UserService] =
      ZLayer.fromFunction(new UserService(_, _))

  object AuditService:
    val live: ZLayer[Database, Nothing, AuditService] =
      ZLayer.fromFunction(new AuditService(_))

  // Database.live is written once and built once — both services share it
  val runnable =
    app.provide(UserService.live, AuditService.live, Database.live, Logger.live)
}


