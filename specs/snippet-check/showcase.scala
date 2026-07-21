//> using scala 3.5.2
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

val cachedConfig: UIO[Config] = ZIO.succeed(Config())

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
  enum AppError:
    case NetworkError(msg: String)
    case ParseError(line: Int)

  def fetchConfig: ZIO[Any, AppError, Config] = ???

  val program: ZIO[Any, Nothing, Config] =
    fetchConfig
      .retry(Schedule.exponential(100.millis) && Schedule.recurs(5))
      .catchAll:
        case AppError.NetworkError(_) => cachedConfig
        case AppError.ParseError(_)   => ZIO.succeed(Config.fallback)
}

// ── Snippet 3: Resource safety ──────────────────────────────────────────
object Snippet3 {
  def analyze(path: String): ZIO[Any, IOException, Stats] =
    ZIO.acquireReleaseWith(openFile(path))(closeFile): file =>
      computeStats(file)

  // Or compose many resources with Scope
  val app: ZIO[Any, Throwable, Unit] =
    ZIO.scoped:
      for
        db   <- Database.connect
        file <- logFile("app.log")
        _    <- runMigrations(db, file)
      yield () // released in reverse order — even on failure or interruption
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
