package zio

import zio.test._
import zio.test.TestAspect._
import zio.process._

sealed trait AppVariant {
  val mainClass: String
}

case object ExplicitFailureApp extends AppVariant {
  override val mainClass = "zio.ExplicitFailureApp"
}
case object FatalDefectApp extends AppVariant {
  override val mainClass = "zio.FatalDefectApp"
}
case object FinalizerExceedsTheTimeoutApp extends AppVariant {
  override val mainClass                = "zio.FinalizerExceedsTheTimeoutApp"
  val gracefulShutdownTimeout: Duration = 5.seconds
}
case object HelloWorldApp extends AppVariant {
  override val mainClass = "zio.HelloWorldApp"
}
case object HelloWorldFinalizersApp extends AppVariant {
  override val mainClass = "zio.HelloWorldFinalizersApp"
}
case object HelloWorldFinalizersAfterInterruptApp extends AppVariant {
  override val mainClass = "zio.HelloWorldFinalizersAfterInterruptApp"
}

object ZIOAppIntegrationSpec extends ZIOSpecDefault {
  def javaExe = Live.live(System.property("zio.coreIntegration.javaExe").someOrFailException)
  def javaJar = Live.live(System.property("zio.coreIntegration.jar").someOrFailException)

  def appCommand(variant: AppVariant, args: String*): Task[Command] = for {
    java <- javaExe
    jar  <- javaJar
  } yield Command(java, Seq("-cp", jar, variant.mainClass) ++ args: _*)

  def spec = suite("ZIOAppIntegrationSpec")(
    test("java executable can be launched") {
      for {
        java     <- javaExe
        exitCode <- Command(java, "-version").successfulExitCode
      } yield assertTrue(exitCode == ExitCode.success)
    },
    test("application jar assembly exists") {
      import java.io.File
      for {
        jar        <- javaJar
        fileExists <- ZIO.succeedBlocking(new File(jar).exists())
      } yield assertTrue(fileExists)
    },
    test("says hello world") {
      for {
        cmd      <- appCommand(HelloWorldApp)
        proc     <- cmd.run
        out      <- proc.stdout.string
        exitCode <- proc.exitCode
      } yield assertTrue(
        out.trim == "Hello, World!",
        exitCode == ExitCode.success
      )
    },
    test("success translates into ExitCode.success") {
      for {
        cmd      <- appCommand(HelloWorldApp)
        exitCode <- cmd.successfulExitCode
      } yield assertTrue(exitCode == ExitCode.success)
    },
    test("failure translates into ExitCode.failure") {
      for {
        cmd      <- appCommand(ExplicitFailureApp)
        exitCode <- cmd.exitCode
      } yield assertTrue(exitCode == ExitCode.failure)
    },
    test("defect translates into ExitCode.failure") {
      for {
        cmd      <- appCommand(FatalDefectApp)
        exitCode <- cmd.exitCode
      } yield assertTrue(exitCode == ExitCode.failure)
    },
    test("execution of finalizers") {
      for {
        cmd      <- appCommand(HelloWorldFinalizersApp)
        proc     <- cmd.run
        out      <- proc.stdout.lines
        exitCode <- proc.exitCode
      } yield assertTrue(
        out.contains("Hello, World!"),
        out.contains("Executing finalizer..."),
        exitCode == ExitCode.success
      )
    },
    test("execution of finalizers on interruption") {
      for {
        cmd               <- appCommand(HelloWorldFinalizersAfterInterruptApp)
        proc              <- cmd.run
        startupOutLatch   <- Promise.make[Nothing, Unit]
        finalizerOutLatch <- Promise.make[Nothing, Unit]
        _ <- proc.stdout.linesStream.tap {
               case "Hello, World! Press Ctrl+C to interrupt..." => startupOutLatch.succeed(())
               case "Executing finalizer..."                     => finalizerOutLatch.succeed(())
               case _                                            => ZIO.unit
             }.runDrain.fork
        _        <- startupOutLatch.await
        _        <- proc.kill
        _        <- finalizerOutLatch.await
        exitCode <- proc.exitCode
      } yield assertTrue(exitCode != ExitCode.success)
    } @@ timeout(10.seconds),
    test("gracefulShutdownTimeout is respected") {
      for {
        cmd             <- appCommand(FinalizerExceedsTheTimeoutApp)
        proc            <- cmd.run
        startupOutLatch <- Promise.make[Nothing, Unit]
        _ <- proc.stdout.linesStream.tap {
               case "Acquiring resource..." => startupOutLatch.succeed(())
               case _                       => ZIO.unit
             }.runDrain.fork
        _ <- startupOutLatch.await
        _ <- proc.kill.timeoutFail(
               new RuntimeException("Graceful shutdown timeout exceeded")
             )(FinalizerExceedsTheTimeoutApp.gracefulShutdownTimeout + 1.second)
        exitCode <- proc.exitCode
      } yield assertTrue(exitCode != ExitCode.success)
    } @@ timeout(30.seconds)
  ) @@ withLiveClock
}
