package scalaz.zio

class RefSpec extends AbstractRTSSpec {

  def is = "RefSpec".title ^ s2"""
   Create a new Ref with a specified value and check if:
      `read` returns the current value.                                                        $e1
      `write` puts the new value correctly.                                                    $e2
      `update` changes the value and returns the updated value.                                $e3
      `updateSome` changes a given type State in some cases and returns the updated value.     $e4
      `modify` changes the value and returns another value computed from the modification.     $e5
    """

  val (current, update) = ("value", "new value")

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

  def e4 = {
    sealed trait State
    case object Active  extends State
    case object Changed extends State
    case object Closed  extends State

    unsafeRun(
      for {
        ref    <- Ref[State](Active)
        value1 <- ref.updateSome { case Active => Changed }
        value2 <- ref.updateSome { case Closed => Active }
        value3 <- ref.updateSome {
                   case Active  => Changed
                   case Changed => Closed
                 }
      } yield
        (value1 must beTheSameAs(Changed)) and (value2 must beTheSameAs(Changed)) and (value3 must beTheSameAs(Closed))
    )
  }

  def e5 =
    unsafeRun(
      for {
        ref   <- Ref(current)
        r     <- ref.modify[String](_ => ("hello", update))
        value <- ref.get
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )

}
