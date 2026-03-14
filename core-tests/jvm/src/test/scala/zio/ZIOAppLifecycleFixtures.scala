package zio

import zio.internal.FiberRuntime

import java.nio.file.{Files, Path, StandardOpenOption}

private[zio] object ZIOAppLifecycleMarker {
  def write(path: Path, content: String): Unit =
    Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
}

/** Fixture: runs forever, writes finalizer marker on interrupt. */
object ZIOAppLifecycleSignalApp extends ZIOAppDefault {
  val run: ZIO[ZIOAppArgs, Any, Any] =
    for {
      args <- getArgs
      _    <- ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(0)), "ready"))
      _    <- ZIO.never.ensuring(ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(1)), "done")).orDie)
    } yield ()
}

/** Fixture: hanging finalizer, 200ms graceful timeout. */
object ZIOAppLifecycleHangingApp extends ZIOAppDefault {
  override val gracefulShutdownTimeout: Duration = 200.millis

  val run: ZIO[ZIOAppArgs, Any, Any] =
    for {
      args <- getArgs
      _    <- ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(0)), "ready"))
      _    <- ZIO.never.ensuring(ZIO.never)
    } yield ()
}

/** Fixture: slow finalizer (500ms), infinite graceful timeout. */
object ZIOAppLifecycleSlowFinalizerApp extends ZIOAppDefault {
  override val gracefulShutdownTimeout: Duration = Duration.Infinity

  val run: ZIO[ZIOAppArgs, Any, Any] =
    for {
      args <- getArgs
      _    <- ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(0)), "ready"))
      _ <- ZIO.never.ensuring {
             ZIO.sleep(500.millis) *>
               ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(1)), "done")).orDie
           }
    } yield ()
}

/** Fixture: sets catastrophic failure flag before running. */
object ZIOAppLifecycleCatastrophicApp extends ZIOAppDefault {
  val run: ZIO[ZIOAppArgs, Any, Any] =
    for {
      args <- getArgs
      _    <- ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(0)), "ready"))
      _    <- ZIO.succeed(FiberRuntime.catastrophicFailure.set(true))
      _    <- ZIO.never.ensuring(ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(1)), "done")).orDie)
    } yield ()
}

/** Fixture: zero graceful timeout — shutdown should not hang. */
object ZIOAppLifecycleZeroTimeoutApp extends ZIOAppDefault {
  override val gracefulShutdownTimeout: Duration = Duration.Zero

  val run: ZIO[ZIOAppArgs, Any, Any] =
    for {
      args <- getArgs
      _    <- ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(0)), "ready"))
      _    <- ZIO.never.ensuring(ZIO.attempt(ZIOAppLifecycleMarker.write(Path.of(args(1)), "done")).orDie)
    } yield ()
}
