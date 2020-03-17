package zio

import zio.test.Assertion._
import zio.test._

object RefMSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec = suite("RefMSpec")(
    testM("get") {
      for {
        refM  <- RefM.make(current)
        value <- refM.get
      } yield assert(value)(equalTo(current))
    },
    testM("getAndUpdate") {
      for {
        refM   <- RefM.make(current)
        value1 <- refM.getAndUpdate(_ => IO.effectTotal(update))
        value2 <- refM.get
      } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
    },
    testM("getAndUpdate with failure") {
      for {
        refM  <- RefM.make[String](current)
        value <- refM.getAndUpdate(_ => IO.fail(failure)).run
      } yield assert(value)(fails(equalTo(failure)))
    },
    testM("getAndUpdateSome") {
      for {
        refM   <- RefM.make[State](Active)
        value1 <- refM.getAndUpdateSome { case Closed => IO.succeedNow(Active) }
        value2 <- refM.get
      } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
    },
    testM("getAndUpdateSome twice") {
      for {
        refM   <- RefM.make[State](Active)
        value1 <- refM.getAndUpdateSome { case Active => IO.succeedNow(Changed) }
        value2 <- refM.getAndUpdateSome {
                   case Active  => IO.succeedNow(Changed)
                   case Changed => IO.succeedNow(Closed)
                 }
        value3 <- refM.get
      } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Changed)) && assert(value3)(equalTo(Closed))
    },
    testM("getAndUpdateSome with failure") {
      for {
        refM  <- RefM.make[State](Active)
        value <- refM.getAndUpdateSome { case Active => IO.fail(failure) }.run
      } yield assert(value)(fails(equalTo(failure)))
    },
    testM("interrupt parent fiber and update") {
      for {
        promise     <- Promise.make[Nothing, RefM[State]]
        latch       <- Promise.make[Nothing, Unit]
        makeAndWait = promise.complete(RefM.make[State](Active)) *> latch.await
        fiber       <- makeAndWait.fork
        refM        <- promise.await
        _           <- fiber.interrupt
        value       <- refM.updateAndGet(_ => ZIO.succeedNow(Closed))
      } yield assert(value)(equalTo(Closed))
    } @@ zioTag(interruption),
    testM("modify") {
      for {
        refM  <- RefM.make(current)
        r     <- refM.modify(_ => IO.effectTotal(("hello", update)))
        value <- refM.get
      } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
    },
    testM("modify with failure") {
      for {
        refM <- RefM.make[String](current)
        r    <- refM.modify(_ => IO.fail(failure)).run
      } yield assert(r)(fails(equalTo(failure)))
    } @@ zioTag(errors),
    testM("modify twice") {
      for {
        refM   <- RefM.make[State](Active)
        r1     <- refM.modifySome("doesn't change the state") { case Active => IO.succeedNow("changed" -> Changed) }
        value1 <- refM.get
        r2 <- refM.modifySome("doesn't change the state") {
               case Active  => IO.succeedNow("changed" -> Changed)
               case Changed => IO.succeedNow("closed"  -> Closed)
             }
        value2 <- refM.get
      } yield assert(r1)(equalTo("changed")) &&
        assert(value1)(equalTo(Changed)) &&
        assert(r2)(equalTo("closed")) &&
        assert(value2)(equalTo(Closed))
    },
    testM("modifySome") {
      for {
        refM  <- RefM.make[State](Active)
        r     <- refM.modifySome("State doesn't change") { case Closed => IO.succeedNow("active" -> Active) }
        value <- refM.get
      } yield assert(r)(equalTo("State doesn't change")) && assert(value)(equalTo(Active))
    },
    testM("modifySome with failure not triggered") {
      for {
        refM  <- RefM.make[State](Active)
        r     <- refM.modifySome("State doesn't change") { case Closed => IO.fail(failure) }.orDieWith(new Exception(_))
        value <- refM.get
      } yield assert(r)(equalTo("State doesn't change")) && assert(value)(equalTo(Active))
    } @@ zioTag(errors),
    testM("modifySome with failure") {
      for {
        refM  <- RefM.make[State](Active)
        value <- refM.modifySome("State doesn't change") { case Active => IO.fail(failure) }.run
      } yield assert(value)(fails(equalTo(failure)))
    } @@ zioTag(errors),
    testM("modifySome with fatal error") {
      for {
        refM  <- RefM.make[State](Active)
        value <- refM.modifySome("State doesn't change") { case Active => IO.dieMessage(fatalError) }.run
      } yield assert(value)(dies(hasMessage(equalTo(fatalError))))
    } @@ zioTag(errors),
    testM("set") {
      for {
        refM  <- RefM.make(current)
        _     <- refM.set(update)
        value <- refM.get
      } yield assert(value)(equalTo(update))
    },
    testM("updateAndGet") {
      for {
        refM  <- RefM.make(current)
        value <- refM.updateAndGet(_ => IO.effectTotal(update))
      } yield assert(value)(equalTo(update))
    },
    testM("updateAndGet with failure") {
      for {
        refM  <- RefM.make[String](current)
        value <- refM.updateAndGet(_ => IO.fail(failure)).run
      } yield assert(value)(fails(equalTo(failure)))
    } @@ zioTag(errors),
    testM("updateSomeAndGet") {
      for {
        refM  <- RefM.make[State](Active)
        value <- refM.updateSomeAndGet { case Closed => IO.succeedNow(Active) }
      } yield assert(value)(equalTo(Active))
    },
    testM("updateSomeAndGet twice") {
      for {
        refM   <- RefM.make[State](Active)
        value1 <- refM.updateSomeAndGet { case Active => IO.succeedNow(Changed) }
        value2 <- refM.updateSomeAndGet {
                   case Active  => IO.succeedNow(Changed)
                   case Changed => IO.succeedNow(Closed)
                 }
      } yield assert(value1)(equalTo(Changed)) && assert(value2)(equalTo(Closed))
    },
    testM("updateSomeAndGet with failure") {
      for {
        refM  <- RefM.make[State](Active)
        value <- refM.updateSomeAndGet { case Active => IO.fail(failure) }.run
      } yield assert(value)(fails(equalTo(failure)))
    } @@ zioTag(errors)
  )

  val (current, update) = ("value", "new value")
  val failure           = "failure"
  val fatalError        = ":-0"

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State
}
