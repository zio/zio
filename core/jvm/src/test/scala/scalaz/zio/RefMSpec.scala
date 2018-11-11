package scalaz.zio

class RefMSpec extends AbstractRTSSpec {

  def is = "RefMSpec".title ^ s2"""
   Create a new RefM with a specified value and check if:
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
        refM  <- RefM(current)
        value <- refM.get
      } yield value must beTheSameAs(current)
    )

  def e2 =
    unsafeRun(
      for {
        refM  <- RefM(current)
        _     <- refM.set(update)
        value <- refM.get
      } yield value must beTheSameAs(update)
    )

  def e3 =
    unsafeRun(
      for {
        refM  <- RefM(current)
        value <- refM.update(_ => IO.sync(update))
      } yield value must beTheSameAs(update)
    )

  def e4 = {
    sealed trait State
    case object Active  extends State
    case object Changed extends State
    case object Closed  extends State

    unsafeRun(
      for {
        refM   <- RefM[State](Active)
        value1 <- refM.updateSome { case Active => IO.now(Changed) }
        value2 <- refM.updateSome { case Closed => IO.now(Active) }
        value3 <- refM.updateSome {
                   case Active  => IO.now(Changed)
                   case Changed => IO.now(Closed)
                 }
      } yield
        (value1 must beTheSameAs(Changed)) and (value2 must beTheSameAs(Changed)) and (value3 must beTheSameAs(Closed))
    )
  }

  def e5 =
    unsafeRun(
      for {
        refM  <- RefM(current)
        r     <- refM.modify[String](_ => IO.sync(("hello", update)))
        value <- refM.get
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )
}
