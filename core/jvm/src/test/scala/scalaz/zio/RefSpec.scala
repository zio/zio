package scalaz.zio

class RefSpec extends AbstractRTSSpec {

  def is = "RefSpec".title ^ s2"""
   Create a new Ref with a specified value and check if:
      `read` returns the current value.                                                        $e1
      `write` puts the new value correctly.                                                    $e2
      `modify` changes the value and returns the updated value.                                $e3
      `modifyFold` changes the value and returns another value computed from the modification. $e4
      `writeLater` puts a new value.                                                           $e5
      `tryWrite` returns
         true and puts a new value to Ref.                                                     $e6
         false and abort if there is a concurrent modification of the value by other fibers.   $e7
      `compareAndSet` returns
        true if the previous value and the current value have the same reference.              $e8
        false if the previous value and the current value have a different reference.          $e9
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

  def e4 =
    unsafeRun(
      for {
        ref   <- Ref(current)
        r     <- ref.modify[String](_ => ("hello", update))
        value <- ref.get
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )

  def e5 =
    unsafeRun(
      for {
        ref   <- Ref(current)
        _     <- ref.setLater(update)
        value <- ref.get
      } yield value must beTheSameAs(update)
    )

  def e6 =
    unsafeRun(
      for {
        ref     <- Ref(current)
        success <- ref.trySet(update)
        value   <- ref.get
      } yield (success must beTrue) and (value must beTheSameAs(update))
    )

  def e7 = {

    def tryWriteUntilFalse(ref: Ref[Int], update: Int): IO[Nothing, Boolean] =
      ref
        .trySet(update)
        .flatMap(success => if (!success) IO.point[Boolean](success) else tryWriteUntilFalse(ref, update))

    unsafeRun(
      for {
        ref     <- Ref(0)
        f1      <- ref.set(1).forever.fork
        f2      <- ref.set(2).forever.fork
        success <- tryWriteUntilFalse(ref, 3)
        value   <- ref.get
        _       <- f1.zipWith(f2)((_, _) => ()).interrupt(new Error("Terminated fiber"))
      } yield (success must beFalse) and (value must be_!=(3))
    )

  }

  def e8 =
    unsafeRun(
      for {
        ref     <- Ref(current)
        success <- ref.compareAndSet(current, update)
        value   <- ref.get
      } yield (success must beTrue) and (value must beTheSameAs(update))
    )

  def e9 =
    unsafeRun(
      for {
        ref     <- Ref(current)
        success <- ref.compareAndSet(update, current)
        value   <- ref.get
      } yield (success must beFalse) and (value must beTheSameAs(current))
    )

}
