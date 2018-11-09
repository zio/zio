package scalaz.zio

class RefMSpec extends AbstractRTSSpec {

  def is = "RefMSpec".title ^ s2"""
   Create a new RefM with a specified value and check if:
      `read` returns the current value.                                                        $e1
      `write` puts the new value correctly.                                                    $e2
      `modify` changes the value and returns the updated value.                                $e3
      `modifyFold` changes the value and returns another value computed from the modification. $e4
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

  def e4 =
    unsafeRun(
      for {
        refM  <- RefM(current)
        r     <- refM.modify[String](_ => IO.sync(("hello", update)))
        value <- refM.get
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )
}
