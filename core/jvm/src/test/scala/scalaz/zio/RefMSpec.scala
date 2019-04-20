package scalaz.zio

class RefMSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "RefMSpec".title ^ s2"""
   Create a new RefM with a specified value and check if:
      `read` returns the current value.                                                                         $e1
      `write` puts the new value correctly.                                                                     $e2
      `update` changes the value and returns the updated value.                                                 $e3
      `update` returns an error if update effect failed                                                         $e4
      `updateSome` changes a given type State in some cases and returns the updated value.                      $e5
      `updateSome` returns the old value for an undefined State.                                                $e6
      `updateSome` returns an error if update effect failed                                                     $e7
      `modify` changes the value and returns another value computed from the modification.                      $e8
      `modify` returns a error if modification effect failed                                                    $e9
      `modifySome` changes a given type State in some cases and returns a value computed from the modification. $e10
      `modifySome` returns a default value without modifying the State.                                         $e11
      `modifySome` returns a default value if modification effect failed                                        $e12
      `modifySome` returns a error if modification effect failed                                                $e13
    """

  val (current, update) = ("value", "new value")
  val fail              = "fail"

  sealed trait State
  case object Active  extends State
  case object Changed extends State
  case object Closed  extends State

  def e1 =
    unsafeRun(
      for {
        refM  <- RefM.make(current)
        value <- refM.get
      } yield value must beTheSameAs(current)
    )

  def e2 =
    unsafeRun(
      for {
        refM  <- RefM.make(current)
        _     <- refM.set(update)
        value <- refM.get
      } yield value must beTheSameAs(update)
    )

  def e3 =
    unsafeRun(
      for {
        refM  <- RefM.make(current)
        value <- refM.update(_ => IO.effectTotal(update))
      } yield value must beTheSameAs(update)
    )

  def e4 =
    unsafeRun(
      (for {
        refM  <- RefM.make[String](current)
        value <- refM.update(_ => IO.fail(fail))
      } yield value).flip.map(_ must beTheSameAs(fail))
    )

  def e5 =
    unsafeRun(
      for {
        refM   <- RefM.make[State](Active)
        value1 <- refM.updateSome { case Active => IO.succeed(Changed) }
        value2 <- refM.updateSome {
                   case Active  => IO.succeed(Changed)
                   case Changed => IO.succeed(Closed)
                 }
      } yield (value1 must beTheSameAs(Changed)) and (value2 must beTheSameAs(Closed))
    )

  def e6 =
    unsafeRun(
      for {
        refM  <- RefM.make[State](Active)
        value <- refM.updateSome { case Closed => IO.succeed(Active) }
      } yield value must beTheSameAs(Active)
    )

  def e7 =
    unsafeRun(
      (for {
        refM <- RefM.make[State](Active)
        _    <- refM.updateSome { case Active => IO.fail(fail) }
        value2 <- refM.updateSome {
                   case Active  => IO.succeed(Changed)
                   case Changed => IO.succeed(Closed)
                 }
      } yield value2).flip.map(_ must beTheSameAs(fail))
    )

  def e8 =
    unsafeRun(
      for {
        refM  <- RefM.make(current)
        r     <- refM.modify(_ => IO.effectTotal(("hello", update)))
        value <- refM.get
      } yield (r must beTheSameAs("hello")) and (value must beTheSameAs(update))
    )

  def e9 =
    unsafeRun(
      (for {
        refM <- RefM.make[String](current)
        r    <- refM.modify(_ => IO.fail(fail))
      } yield r).flip map (_ must beTheSameAs(fail))
    )

  def e10 =
    unsafeRun(
      for {
        refM   <- RefM.make[State](Active)
        r1     <- refM.modifySome("doesn't change the state") { case Active => IO.succeed("changed" -> Changed) }
        value1 <- refM.get
        r2 <- refM.modifySome("doesn't change the state") {
               case Active  => IO.succeed("changed" -> Changed)
               case Changed => IO.succeed("closed"  -> Closed)
             }
        value2 <- refM.get
      } yield
        (r1 must beTheSameAs("changed")) and (value1 must beTheSameAs(Changed)) and (r2 must beTheSameAs("closed")) and (value2 must beTheSameAs(
          Closed
        ))
    )

  def e11 =
    unsafeRun(
      for {
        refM  <- RefM.make[State](Active)
        r     <- refM.modifySome("State doesn't change") { case Closed => IO.succeed("active" -> Active) }
        value <- refM.get
      } yield (r must beTheSameAs("State doesn't change")) and (value must beTheSameAs(Active))
    )

  def e12 =
    unsafeRun(
      for {
        refM  <- RefM.make[State](Active)
        r     <- refM.modifySome("State doesn't change") { case Closed => IO.fail(fail) }
        value <- refM.get
      } yield (r must beTheSameAs("State doesn't change")) and (value must beTheSameAs(Active))
    )

  def e13 =
    unsafeRun(
      (for {
        refM  <- RefM.make[State](Active)
        _     <- refM.modifySome("State doesn't change") { case Active => IO.fail(fail) }
        value <- refM.get
      } yield value).flip.map(_ must beTheSameAs(fail))
    )
}
