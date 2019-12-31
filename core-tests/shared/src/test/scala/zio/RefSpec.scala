package zio

import zio.test._
import zio.test.Assertion._

object RefSpec extends ZIOBaseSpec {

  def spec = suite("RefSpec")(
    testM("get") {
      for {
        ref   <- Ref.make(current)
        value <- ref.get
      } yield assert(value)(equalTo(current))
    },
    testM("set") {
      for {
        ref   <- Ref.make(current)
        _     <- ref.set(update)
        value <- ref.get
      } yield assert(value)(equalTo(update))
    },
    testM("update") {
      for {
        ref   <- Ref.make(current)
        value <- ref.update(_ => update)
      } yield assert(value)(equalTo(update))
    },
    testM("updateSome") {
      for {
        ref   <- Ref.make[State](Active)
        value <- ref.updateSome { case Closed => Changed }
      } yield assert(value)(equalTo(Active))
    },
    testM("updateSome twice") {
      for {
        ref    <- Ref.make[State](Active)
        value1 <- ref.updateSome { case Active => Changed }
        value2 <- ref.updateSome {
                   case Active  => Changed
                   case Changed => Closed
                 }
      } yield assert(value1)(equalTo(Changed)) && assert(value2)(equalTo(Closed))
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
    }
  )

  val (current, update) = ("value", "new value")

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State

}
