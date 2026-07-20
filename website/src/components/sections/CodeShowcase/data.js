// Each snippet is compile-checked by specs/snippet-check/showcase.scala.
// If you edit a snippet here, update that file and re-run:
//   scala-cli compile specs/snippet-check/showcase.scala
export const examples = [
  {
    value: 'concurrency',
    label: 'Concurrency',
    takeaway:
      'Fibers, not threads — parallelism is one combinator, and interruption is handled for you.',
    points: [
      'Fibers are lightweight — run thousands concurrently, not OS threads.',
      'Work runs in parallel; if one part fails, the rest are interrupted.',
      'The same safety holds for one task or a whole collection.',
    ],
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
    points: [
      'Every possible failure is visible in the type, not hidden in exceptions.',
      'Built-in retry policies recover from transient failures with backoff.',
      'The compiler proves when every error has been handled.',
    ],
    code: `enum AppError:
  case NetworkError(msg: String)
  case ParseError(line: Int)

def fetchConfig: ZIO[Any, AppError, Config] = ???

val program: ZIO[Any, Nothing, Config] =
  fetchConfig
    .retry(Schedule.exponential(100.millis) && Schedule.recurs(5))
    .catchAll:
      case AppError.NetworkError(_) => cachedConfig
      case AppError.ParseError(_)   => ZIO.succeed(Config.fallback)`,
  },
  {
    value: 'resources',
    label: 'Resource safety',
    takeaway:
      'Acquire and release are paired at the type level — leaks are impossible, even under interruption.',
    points: [
      'Acquire and release are paired, so cleanup always runs.',
      'Many resources compose and close in reverse order.',
      'Guaranteed on success, failure, or interruption alike.',
    ],
    code: `def analyze(path: String): ZIO[Any, IOException, Stats] =
  ZIO.acquireReleaseWith(openFile(path))(closeFile): file =>
    computeStats(file)

// Or compose many resources with Scope
val app: ZIO[Any, Throwable, Unit] =
  ZIO.scoped:
    for
      db   <- Database.connect
      file <- logFile("app.log")
      _    <- runMigrations(db, file)
    yield () // released in reverse order — even on failure or interruption`,
  },
  {
    value: 'streaming',
    label: 'Streaming',
    takeaway:
      'Infinite data, finite memory — backpressure and concurrency built into every stage.',
    points: [
      'Data is processed incrementally — unbounded sources, finite memory.',
      'Stages run concurrently while preserving order.',
      'Backpressure flows through the pipeline automatically.',
    ],
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
    points: [
      'Each service declares its dependencies in its type.',
      'Services are accessed from the environment with no manual wiring.',
      'A missing dependency is a compile error, not a runtime failure.',
    ],
    code: `class UserService(db: Database, logger: Logger):
  def signup(name: String): Task[User] =
    logger.info(s"signing up $name") *> db.insert(name)

object UserService:
  val live: ZLayer[Database & Logger, Nothing, UserService] =
    ZLayer.fromFunction(new UserService(_, _))

val app: ZIO[UserService, Throwable, User] =
  ZIO.serviceWithZIO[UserService](_.signup("John"))

// Compile-time-checked wiring: forget a layer and the build fails
val runnable = app.provide(UserService.live, Database.live, Logger.live)`,
  },
];
