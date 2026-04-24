package zio

import zio.test.Assertion._
import zio.test._

object RefSynchronizedSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("RefSynchronizedSpec")(
    suite("atomic")(
      test("get") {
        for {
          ref   <- Ref.Synchronized.make(current)
          value <- ref.get
        } yield assert(value)(equalTo(current))
      },
      test("getAndUpdate") {
        for {
          ref    <- Ref.Synchronized.make(current)
          value1 <- ref.getAndUpdateZIO(_ => ZIO.succeed(update))
          value2 <- ref.get
        } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
      },
      test("getAndUpdate with failure") {
        for {
          ref   <- Ref.Synchronized.make[String](current)
          value <- ref.getAndUpdateZIO(_ => ZIO.fail(failure)).exit
        } yield assert(value)(fails(equalTo(failure)))
      },
      test("getAndUpdateSome") {
        for {
          ref    <- Ref.Synchronized.make[State](Active)
          value1 <- ref.getAndUpdateSomeZIO { case Closed => ZIO.succeed(Active) }
          value2 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
      },
      test("getAndUpdateSome twice") {
        for {
          ref    <- Ref.Synchronized.make[State](Active)
          value1 <- ref.getAndUpdateSomeZIO { case Active => ZIO.succeed(Changed) }
          value2 <- ref.getAndUpdateSomeZIO {
                      case Active  => ZIO.succeed(Changed)
                      case Changed => ZIO.succeed(Closed)
                    }
          value3 <- ref.get
        } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Changed)) && assert(value3)(equalTo(Closed))
      },
      test("getAndUpdateSome with failure") {
        for {
          ref   <- Ref.Synchronized.make[State](Active)
          value <- ref.getAndUpdateSomeZIO { case Active => ZIO.fail(failure) }.exit
        } yield assert(value)(fails(equalTo(failure)))
      },
      test("interrupt parent fiber and update") {
        for {
          promise    <- Promise.make[Nothing, Ref.Synchronized[State]]
          latch      <- Promise.make[Nothing, Unit]
          makeAndWait = promise.complete(Ref.Synchronized.make[State](Active)) *> latch.await
          fiber      <- makeAndWait.fork
          ref        <- promise.await
          _          <- fiber.interrupt
          value      <- ref.updateAndGetZIO(_ => ZIO.succeed(Closed))
        } yield assert(value)(equalTo(Closed))
      } @@ zioTag(interruption),
      test("modify") {
        for {
          ref   <- Ref.Synchronized.make(current)
          r     <- ref.modifyZIO(_ => ZIO.succeed(("hello", update)))
          value <- ref.get
        } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
      },
      test("modify with failure") {
        for {
          ref <- Ref.Synchronized.make[String](current)
          r   <- ref.modifyZIO(_ => ZIO.fail(failure)).exit
        } yield assert(r)(fails(equalTo(failure)))
      } @@ zioTag(errors),
      test("modify twice") {
        for {
          ref    <- Ref.Synchronized.make[State](Active)
          r1     <- ref.modifySomeZIO("doesn't change the state") { case Active => ZIO.succeed("changed" -> Changed) }
          value1 <- ref.get
          r2 <- ref.modifySomeZIO("doesn't change the state") {
                  case Active  => ZIO.succeed("changed" -> Changed)
                  case Changed => ZIO.succeed("closed" -> Closed)
                }
          value2 <- ref.get
        } yield assert(r1)(equalTo("changed")) &&
          assert(value1)(equalTo(Changed)) &&
          assert(r2)(equalTo("closed")) &&
          assert(value2)(equalTo(Closed))
      },
      test("modifySome") {
        for {
          ref   <- Ref.Synchronized.make[State](Active)
          r     <- ref.modifySomeZIO("State doesn't change") { case Closed => ZIO.succeed("active" -> Active) }
          value <- ref.get
        } yield assert(r)(equalTo("State doesn't change")) && assert(value)(equalTo(Active))
      },
      test("modifySome with failure not triggered") {
        for {
          ref <- Ref.Synchronized.make[State](Active)
          r <-
            ref.modifySomeZIO("State doesn't change") { case Closed => ZIO.fail(failure) }.orDieWith(new Exception(_))
          value <- ref.get
        } yield assert(r)(equalTo("State doesn't change")) && assert(value)(equalTo(Active))
      } @@ zioTag(errors),
      test("modifySome with failure") {
        for {
          ref   <- Ref.Synchronized.make[State](Active)
          value <- ref.modifySomeZIO("State doesn't change") { case Active => ZIO.fail(failure) }.exit
        } yield assert(value)(fails(equalTo(failure)))
      } @@ zioTag(errors),
      test("modifySome with fatal error") {
        for {
          ref   <- Ref.Synchronized.make[State](Active)
          value <- ref.modifySomeZIO("State doesn't change") { case Active => ZIO.dieMessage(fatalError) }.exit
        } yield assert(value)(dies(hasMessage(equalTo(fatalError))))
      } @@ zioTag(errors),
      test("set") {
        for {
          ref   <- Ref.Synchronized.make(current)
          _     <- ref.set(update)
          value <- ref.get
        } yield assert(value)(equalTo(update))
      },
      test("updateAndGet") {
        for {
          ref   <- Ref.Synchronized.make(current)
          value <- ref.updateAndGetZIO(_ => ZIO.succeed(update))
        } yield assert(value)(equalTo(update))
      },
      test("updateAndGet with failure") {
        for {
          ref   <- Ref.Synchronized.make[String](current)
          value <- ref.updateAndGetZIO(_ => ZIO.fail(failure)).exit
        } yield assert(value)(fails(equalTo(failure)))
      } @@ zioTag(errors),
      test("updateSomeAndGet") {
        for {
          ref   <- Ref.Synchronized.make[State](Active)
          value <- ref.updateSomeAndGetZIO { case Closed => ZIO.succeed(Active) }
        } yield assert(value)(equalTo(Active))
      },
      test("updateSomeAndGet twice") {
        for {
          ref    <- Ref.Synchronized.make[State](Active)
          value1 <- ref.updateSomeAndGetZIO { case Active => ZIO.succeed(Changed) }
          value2 <- ref.updateSomeAndGetZIO {
                      case Active  => ZIO.succeed(Changed)
                      case Changed => ZIO.succeed(Closed)
                    }
        } yield assert(value1)(equalTo(Changed)) && assert(value2)(equalTo(Closed))
      },
      test("updateSomeAndGet with failure") {
        for {
          ref   <- Ref.Synchronized.make[State](Active)
          value <- ref.updateSomeAndGetZIO { case Active => ZIO.fail(failure) }.exit
        } yield assert(value)(fails(equalTo(failure)))
      } @@ zioTag(errors)
    )
  )

  val (current, update) = ("value", "new value")
  val failure           = "failure"
  val fatalError        = ":-0"

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State
}
