package scalaz.zio

class RefSpec extends AbstractRTSSpec {

  def is = "RefSpec".title ^ s2"""
   Create a new Ref with a specified value and check if:
      `read` returns the current value.                                                                         $e1
      `write` puts the new value correctly.                                                                     $e2
      `update` changes the value and returns the updated value.                                                 $e3
      `updateSome` changes a given type State in some cases and returns the updated value.                      $e4
      `updateSome` returns the old value for an undefined State.                                                $e5
      `modify` changes the value and returns another value computed from the modification.                      $e6
      `modifySome` changes a given type State in some cases and returns a value computed from the modification. $e7
      `modifySome` returns a default value without modifying the State.                                         $e8
    """

  val (current, update) = ("value", "new value")

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State

  def e1 =
    unsafeRun(
      for {
        ref   <- Ref(current)
        value <- ref.get
      } yield value must beTheSameAs(current)
    )

  def e2 =
    unsafeRun(
      for {
        ref   <- Ref(current)
        _     <- ref.set(update)
        value <- ref.get
      } yield value must beTheSameAs(update)
    )

  def e3 =
    unsafeRun(
      for {
        ref   <- Ref(current)
        value <- ref.update(_ => update)
      } yield value must beTheSameAs(update)
    )

  def e4 =
    unsafeRun(
      for {
        ref    <- Ref[State](Active)
        value1 <- ref.updateSome { case Active => Changed }
        value2 <- ref.updateSome {
                   case Active  => Changed
                   case Changed => Closed
                 }
      } yield (value1 must beTheSameAs(Changed)) and (value2 must beTheSameAs(Closed))
    )

  def e5 =
    unsafeRun(
      for {
        ref   <- Ref[State](Active)
        value <- ref.updateSome { case Closed => Changed }
      } yield value must beTheSameAs(Active)
    )

  def e6 =
    unsafeRun(
      for {
        ref   <- Ref(current)
        r     <- ref.modify[String](_ => ("hello", update))
        value <- ref.get
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )

  def e7 =
    unsafeRun(
      for {
        ref    <- Ref[State](Active)
        value1 <- ref.modifySome[String]("doesn't change the state") { case Active => ("changed", Changed) }
        value2 <- ref.modifySome[String]("doesn't change the state") {
                   case Active  => ("changed", Changed)
                   case Changed => ("closed", Closed)
                 }
      } yield (value1 must beTheSameAs("changed")) and (value2 must beTheSameAs("closed"))
    )

  def e8 =
    unsafeRun(
      for {
        ref   <- Ref[State](Active)
        value <- ref.modifySome[String]("State doesn't change") { case Closed => ("active", Active) }
      } yield value must beTheSameAs("State doesn't change")
    )
}
