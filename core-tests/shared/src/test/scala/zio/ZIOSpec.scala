package zio

import zio.duration._
import zio.test._
import zio.test.mock.live
import zio.test.Assertion._
import zio.Cause._

object ZIOSpec
    extends ZIOBaseSpec(
      suite("ZIO")(
        suite("orElse")(
          testM("left failed and right died with kept cause") {
            val z1                = Task.fail(new Throwable("1"))
            val z2: Task[Nothing] = Task.die(new Throwable("2"))
            val orElse: Task[Boolean] = z1.orElse(z2).catchAllCause {
              case Then(Die(FiberFailure(Traced(Fail(a: Throwable), _))), Traced(Die(b: Throwable), _)) =>
                Task(a.getMessage == "1" && b.getMessage == "2")
              case _ =>
                Task(false)
            }
            assertM(orElse, equalTo(true))
          },
          testM("left failed and right failed with kept cause") {
            val z1                = Task.fail(new Throwable("1"))
            val z2: Task[Nothing] = Task.fail(new Throwable("2"))
            val orElse: Task[Boolean] = z1.orElse(z2).catchAllCause {
              case Then(Die(FiberFailure(Traced(Fail(a: Throwable), _))), Traced(Fail(b: Throwable), _)) =>
                Task(a.getMessage == "1" && b.getMessage == "2")
              case _ =>
                Task(false)
            }
            assertM(orElse, equalTo(true))
          }
        ),
        suite("forkAll")(
          testM("happy-path") {
            val list = (1 to 1000).toList
            assertM(ZIO.forkAll(list.map(a => ZIO.effectTotal(a))).flatMap(_.join), equalTo(list))
          },
          testM("empty input") {
            assertM(ZIO.forkAll(List.empty).flatMap(_.join), equalTo(List.empty))
          },
          testM("propagate failures") {
            val boom = new Exception
            for {
              fiber  <- ZIO.forkAll(List(ZIO.fail(boom)))
              result <- fiber.join.flip
            } yield assert(result, equalTo(boom))
          },
          testM("propagates defects") {
            val boom = new Exception("boom")
            for {
              fiber  <- ZIO.forkAll(List(ZIO.die(boom)))
              result <- fiber.join.sandbox.flip
            } yield assert(result, equalTo(Cause.die(boom)))
          }
        ),
        suite("parallelErrors")(
          testM("oneFailure") {
            for {
              f1     <- IO.fail("error1").fork
              f2     <- IO.succeed("success1").fork
              errors <- f1.zip(f2).join.parallelErrors[String].flip
            } yield assert(errors, equalTo(List("error1")))
          },
          testM("allFailures") {
            for {
              f1     <- IO.fail("error1").fork
              f2     <- IO.fail("error2").fork
              errors <- f1.zip(f2).join.parallelErrors[String].flip
            } yield assert(errors, equalTo(List("error1", "error2")))
          }
        ),
        suite("raceAll")(
          testM("returns first sucess") {
            assertM(ZIO.fail("Fail").raceAll(List(IO.succeed(24))), equalTo(24))
          },
          testM("returns last failure") {
            assertM(live(ZIO.sleep(100.millis) *> ZIO.fail(24)).raceAll(List(ZIO.fail(25))).flip, equalTo(24))
          },
          testM("returns success when it happens after failure") {
            assertM(ZIO.fail(42).raceAll(List(IO.succeed(24) <* live(ZIO.sleep(100.millis)))), equalTo(24))
          }
        )
      )
    )
