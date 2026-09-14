// Each snippet is compile-checked by specs/snippet-check/showcase.scala.
// If you edit a snippet here, update that file and re-run:
//   scala-cli compile specs/snippet-check/showcase.scala
import { STREAMING_SNIPPET } from '../../visual-effects/streamingParams';

export const examples = [
  {
    value: 'concurrency',
    label: 'Concurrency',
    visual: 'concurrency',
    takeaway:
      'Fibers, not threads — parallelism is one combinator, and interruption is handled for you.',
    points: [
      'Fibers are lightweight — run thousands concurrently, not OS threads.',
      'Work runs in parallel; if one part fails, the rest are interrupted.',
      'The same safety holds for one task or a whole collection.',
    ],
    code: `val tortoise = runFast("tortoise")
val achilles = runFast("achilles")

val winner = tortoise.race(achilles)`,
  },
  {
    value: 'errors',
    label: 'Error handling',
    visual: 'errors',
    takeaway:
      'Failures are transient by default — retry policies recover without hand-written retry loops.',
    points: [
      'Retry with backoff is one combinator, not a hand-rolled loop with counters and sleeps.',
      'Schedules compose — exponential backoff, jitter, and limits combine declaratively.',
      'The same retry logic works for any effect, from a single call to a whole pipeline.',
    ],
    code: `val park = attemptParallelPark()
val result = park.retry(Schedule.exponential(700.millis))`,
  },
  {
    value: 'resources',
    label: 'Resource safety',
    visual: 'resources',
    takeaway:
      'Acquire and release are paired at the type level — leaks are impossible, even under interruption.',
    points: [
      'Acquire and release are paired, so cleanup always runs.',
      'Many resources compose and close in reverse order.',
      'Guaranteed on success, failure, or interruption alike.',
    ],
    code: `val result: ZIO[Any, Throwable, Report] =
  ZIO.scoped:
    for
      db     <- ZIO.acquireRelease(connectDatabase())(db => ZIO.succeedBlocking(db.close()))
      cache  <- ZIO.acquireRelease(connectCache())(cache => ZIO.succeedBlocking(cache.flush()))
      logger <- ZIO.acquireRelease(openLogFile())(file => ZIO.succeedBlocking(file.close()))
      r      <- doWork(db, cache, logger)
    yield r`,
  },
  {
    value: 'streaming',
    label: 'Streaming',
    visual: 'streaming',
    takeaway:
      'Infinite data, finite memory — backpressure and concurrency built into every stage.',
    points: [
      'Data is processed incrementally — unbounded sources, finite memory.',
      'Stages run concurrently while preserving order.',
      'Backpressure flows through the pipeline automatically.',
    ],
    // Shared with the Visual view's embedded example so the two can never
    // disagree — including on the parameter values.
    code: STREAMING_SNIPPET,
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
