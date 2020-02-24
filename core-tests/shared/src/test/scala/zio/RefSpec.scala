package zio

import zio.test.Assertion._
import zio.test._

object RefSpec extends ZIOBaseSpec {

  def spec = suite("RefSpec")(
    testM("get") {
      for {
        ref   <- Ref.make(current)
        value <- ref.get
      } yield assert(value)(equalTo(current))
    },
    testM("getAndUpdate") {
      for {
        ref    <- Ref.make(current)
        value1 <- ref.getAndUpdate(_ => update)
        value2 <- ref.get
      } yield assert(value1)(equalTo(current)) && assert(value2)(equalTo(update))
    },
    testM("getAndUpdateSome") {
      for {
        ref    <- Ref.make[State](Active)
        value1 <- ref.getAndUpdateSome { case Closed => Changed }
        value2 <- ref.get
      } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Active))
    },
    testM("getAndUpdateSome twice") {
      for {
        ref    <- Ref.make[State](Active)
        value1 <- ref.getAndUpdateSome { case Active => Changed }
        value2 <- ref.getAndUpdateSome {
                   case Active  => Changed
                   case Changed => Closed
                 }
        value3 <- ref.get
      } yield assert(value1)(equalTo(Active)) && assert(value2)(equalTo(Changed)) && assert(value3)(equalTo(Closed))
    },
    testM("modify") {
      for {
        ref   <- Ref.make(current)
        r     <- ref.modify[String](_ => ("hello", update))
        value <- ref.get
      } yield assert(r)(equalTo("hello")) && assert(value)(equalTo(update))
    },
    testM("modifySome") {
      for {
        ref   <- Ref.make[State](Active)
        value <- ref.modifySome[String]("State doesn't change") { case Closed => ("active", Active) }
      } yield assert(value)(equalTo("State doesn't change"))
    },
    testM("modifySome twice") {
      for {
        ref    <- Ref.make[State](Active)
        value1 <- ref.modifySome[String]("doesn't change the state") { case Active => ("changed", Changed) }
        value2 <- ref.modifySome[String]("doesn't change the state") {
                   case Active  => ("changed", Changed)
                   case Changed => ("closed", Closed)
                 }
      } yield assert(value1)(equalTo("changed")) && assert(value2)(equalTo("closed"))
    },
    testM("set") {
      for {
        ref   <- Ref.make(current)
        _     <- ref.set(update)
        value <- ref.get
      } yield assert(value)(equalTo(update))
    },
    testM("updateAndGet") {
      for {
        ref   <- Ref.make(current)
        value <- ref.updateAndGet(_ => update)
      } yield assert(value)(equalTo(update))
    },
    testM("updateSomeAndGet") {
      for {
        ref   <- Ref.make[State](Active)
        value <- ref.updateSomeAndGet { case Closed => Changed }
      } yield assert(value)(equalTo(Active))
    },
    testM("updateSomeAndGet twice") {
      for {
        ref    <- Ref.make[State](Active)
        value1 <- ref.updateSomeAndGet { case Active => Changed }
        value2 <- ref.updateSomeAndGet {
                   case Active  => Changed
                   case Changed => Closed
                 }
      } yield assert(value1)(equalTo(Changed)) && assert(value2)(equalTo(Closed))
    }
  )

  val (current, update) = ("value", "new value")

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State

}
