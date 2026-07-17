// Each snippet is compile-checked by specs/snippet-check/showcase.scala.
// If you edit a snippet here, update that file and re-run:
//   scala-cli compile specs/snippet-check/showcase.scala
export const examples = [
  {
    value: 'concurrency',
    label: 'Concurrency',
    takeaway:
      'Fibers, not threads — parallelism is one combinator, and interruption is handled for you.',
    description:
      'Fibers are lightweight, user-space threads, so you can spin up thousands of them without touching the OS scheduler. zipPar runs two effects concurrently and, if either one fails, automatically interrupts the other so no fiber is left running in the background. ZIO.foreachPar extends the same guarantee to a whole collection of effects at once.',
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
    description:
      'The failure channel of ZIO[R, E, A] is a real type, so every possible error is visible in the signature instead of hiding in a thrown exception. Schedule.exponential composed with recurs(5) retries with exponential backoff for a bounded number of attempts before giving up. catchAll pattern-matches on every error case, and once all of them are handled the resulting effect can fail with Nothing — the compiler proves there is nothing left unhandled.',
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
    description:
      "ZIO.acquireReleaseWith ties a resource's acquisition and release together so the release action always runs, whether the effect succeeds, fails, or is interrupted midway. Scope generalizes this to many resources at once: acquiring a database connection and a log file inside ZIO.scoped guarantees both are closed in reverse order the moment the scope ends. There is no separate finalizer to forget, so leaks are ruled out at the type level rather than by convention.",
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
    description:
      'ZStream processes data incrementally, so a pipeline can consume from an unbounded source like Kafka or a socket without ever holding the whole dataset in memory. mapZIOPar(20) enriches up to 20 elements concurrently while preserving order, and grouped(100) batches results before a single write, cutting down round trips to the database. Backpressure flows through every stage automatically, so a slow database write naturally slows down the upstream producer.',
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
    description:
      'A ZLayer describes how to build a service and what it depends on, letting UserService.live declare its need for Database and Logger directly in its type. ZIO.serviceWithZIO accesses a service from the environment without any manual wiring at the call site. Calling provide with only some of the required layers is a compile error, not a runtime surprise, because the missing dependency shows up in the required environment type R.',
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
