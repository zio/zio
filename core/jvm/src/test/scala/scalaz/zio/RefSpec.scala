package scalaz.zio

import org.specs2.Specification

class RefSpec extends Specification with RTS {

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
        ref   <- Ref[Void, String](current)
        value <- ref.read
      } yield value must beTheSameAs(current)
    )

  def e2 =
    unsafeRun(
      for {
        ref   <- Ref[Void, String](current)
        _     <- ref.write[Void](update)
        value <- ref.read
      } yield value must beTheSameAs(update)
    )

  def e3 =
    unsafeRun(
      for {
        ref   <- Ref[Void, String](current)
        value <- ref.modify(_ => update)
      } yield value must beTheSameAs(update)
    )

  def e4 =
    unsafeRun(
      for {
        ref   <- Ref[Void, String](current)
        r     <- ref.modifyFold[Void, String](_ => ("hello", update))
        value <- ref.read
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )

  def e5 =
    unsafeRun(
      for {
        ref   <- Ref[Void, String](current)
        _     <- ref.writeLater[Void](update)
        value <- ref.read
      } yield value must beTheSameAs(update)
    )

  def e6 =
    unsafeRun(
      for {
        ref     <- Ref[Void, String](current)
        success <- ref.tryWrite[Void](update)
        value   <- ref.read
      } yield (success must beTrue) and (value must beTheSameAs(update))
    )

  def e7 = {

    def tryWriteUntilFalse(ref: Ref[Int], update: Int): IO[Void, Boolean] =
      ref
        .tryWrite[Void](update)
        .flatMap(success => if (!success) IO.point[Void, Boolean](success) else tryWriteUntilFalse(ref, update))

    unsafeRun(
      for {
        ref     <- Ref[Void, Int](0)
        f1      <- ref.write[Void](1).forever[Unit].fork[Void]
        f2      <- ref.write[Void](2).forever[Unit].fork[Void]
        success <- tryWriteUntilFalse(ref, 3)
        value   <- ref.read[Void]
        _       <- f1.zipWith(f2)((_, _) => ()).interrupt[Void]
      } yield (success must beFalse) and (value must be_!=(3))
    )

  }

  def e8 =
    unsafeRun(
      for {
        ref     <- Ref[Void, String](current)
        success <- ref.compareAndSet[Void](current, update)
        value   <- ref.read
      } yield (success must beTrue) and (value must beTheSameAs(update))
    )

  def e9 =
    unsafeRun(
      for {
        ref     <- Ref[Void, String](current)
        success <- ref.compareAndSet[Void](update, current)
        value   <- ref.read
      } yield (success must beFalse) and (value must beTheSameAs(current))
    )

}
