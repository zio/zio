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
case class Stats()
case class Event(isValid: Boolean)
class File { def close(): Unit = () }

class Database { def insert(name: String): Task[User] = ZIO.succeed(User(name)) }
object Database {
  val connect: ZIO[Scope, Throwable, Database] = ZIO.succeed(new Database)
  val live: ULayer[Database]                   = ZLayer.succeed(new Database)
}

class Logger { def info(msg: String): UIO[Unit] = ZIO.unit }
object Logger { val live: ULayer[Logger] = ZLayer.succeed(new Logger) }

def runFast(name: String): Task[String] = ZIO.succeed(name)
def attemptParallelPark(): IO[String, String] = ZIO.succeed("parked")

def logFile(path: String): ZIO[Scope, Throwable, File] = ZIO.succeed(new File)
def runMigrations(db: Database, f: File): Task[Unit]   = ZIO.unit

class DbConn { def close(): Unit = () }
class CacheConn { def flush(): Unit = () }
def connectDatabase(): Task[DbConn] = ZIO.succeed(new DbConn)
def connectCache(): Task[CacheConn] = ZIO.succeed(new CacheConn)
def openLogFile(): IO[IOException, File] = ZIO.succeed(new File)
def doWork(db: DbConn, cache: CacheConn, logger: File): Task[Stats] = ZIO.succeed(Stats())

val events: List[Event]                    = List(Event(true))
def enrich(e: Event): Task[Event]          = ZIO.succeed(e)
def writeBatch(c: Chunk[Event]): Task[Unit] = ZIO.unit

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
  val makeDatabase = ZIO.acquireRelease(connectDatabase())(db => ZIO.succeed(db.close()))
  val makeCache     = ZIO.acquireRelease(connectCache())(cache => ZIO.succeed(cache.flush()))
  val makeLogger    = ZIO.acquireRelease(openLogFile())(file => ZIO.succeed(file.close()))

  val result: ZIO[Any, Throwable, Stats] =
    ZIO.scoped:
      for
        db     <- makeDatabase
        cache  <- makeCache
        logger <- makeLogger
        r      <- doWork(db, cache, logger)
      yield r
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
  class UserService(db: Database, logger: Logger):
    def signup(name: String): Task[User] =
      logger.info(s"signing up $name") *> db.insert(name)

  object UserService:
    val live: ZLayer[Database & Logger, Nothing, UserService] =
      ZLayer.fromFunction(new UserService(_, _))

  val app: ZIO[UserService, Throwable, User] =
    ZIO.serviceWithZIO[UserService](_.signup("John"))

  // Compile-time-checked wiring: forget a layer and the build fails
  val runnable = app.provide(UserService.live, Database.live, Logger.live)
}
