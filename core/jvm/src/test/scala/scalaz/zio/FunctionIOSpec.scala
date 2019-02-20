package scalaz.zio

import FunctionIO._

class FunctionIOSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {
  def is = "FunctionIOSpec".title ^ s2"""
   Check if the functions in `FunctionIO` work correctly
     `lift` lifts from A => B into effectful function $e1
     `identity` returns the identity of the input without modification $e2
     `>>>` is a symbolic operator of `andThen`which does a Backwards composition of effectul functions $e3
     `<<<` is a symbolic operator of `compose` which compses two effectful functions $e4
     `zipWith` zips the output of two effectful functions $e5
     `&&&` zips the output of two effectful functions and returns a tuple of their result $e6
     `|||` computes two effectful functions left and right from from an Either input $e7
     `first` returns a tuple: the output on the first element and input on the second element $e8
     `second` returns a tuple: the input on the first element and output on the second element $e9
     `left` takes an Either as input and computes it if it is Left otherwise returns the same value of the input $e10
     `right`takes an Either as input and computes it if it is Right otherwise returns the same value of the input   $e11
     `asEffect` returns the input value $e12
     `test` check a condition and returns an Either output: Left if the condition is true otherwise false $e13
     `ifThenElse` check an impure condition if it is true then computes an effectful function `then0` else computes `else0` $e14a
     `ifThenElse` check a pure condition if it is true then computes an effectful function `then0` else computes `else0` $e14b
     `whileDo` take a condition and run the body until the condition will be  false with impure function $e15a
     `whileDo` take a condition and run the body until the condition will be  false with pure function $e15b
     `_1` extracts out the first element of a tupe $e16
     `_2` extracts out the second element of a tupe $e17
     `fail` returns a failure  $e18a
     `impure` can translate an Exception to an error  $e18b
    """

  def e1 =
    unsafeRun(
      for {
        v <- lift[Int, Int](_ + 1).run(4)
      } yield v must_=== 5
    )

  def e2 =
    unsafeRun(
      for {
        v <- identity[Int].run(1)
      } yield v must_=== 1
    )

  def e3 =
    unsafeRun(
      for {
        v <- (lift[Int, Int](_ + 1) >>> lift[Int, Int](_ * 2)).run(6)
      } yield v must_=== 14
    )

  def e4 =
    unsafeRun(
      for {
        v <- (lift[Int, Int](_ + 1) <<< lift[Int, Int](_ * 2)).run(6)
      } yield v must_=== 13
    )

  def e5 =
    unsafeRun(
      for {
        v <- succeedLazy(1)
              .zipWith[Nothing, Int, Int, Int](succeedLazy(2))((a, b) => a + b)
              .run(1)
      } yield v must_=== 3
    )

  def e6 =
    unsafeRun(
      for {
        v <- (lift[Int, Int](_ + 1) &&& lift[Int, Int](_ * 2)).run(6)
      } yield (v._1 must_=== 7) and (v._2 must_=== 12)
    )

  def e7 =
    unsafeRun(
      for {
        l <- (lift[Int, Int](_ + 1) ||| lift[Int, Int](_ * 2)).run(Left(25))
        r <- (lift[List[Int], Int](_.sum) ||| lift[List[Int], Int](_.size))
              .run(Right(List(1, 3, 5, 2, 8)))
      } yield (l must_=== 26) and (r must_=== 5)
    )

  def e8 =
    unsafeRun(
      for {
        v <- lift[Int, Int](_ * 2).first.run(100)
      } yield (v._1 must_=== 200) and (v._2 must_=== 100)
    )

  def e9 =
    unsafeRun(
      for {
        v <- lift[Int, Int](_ * 2).second.run(100)
      } yield (v._1 must_=== 100) and (v._2 must_=== 200)
    )
  def e10 =
    unsafeRun(
      for {
        v1 <- lift[Int, Int](_ * 2).left[Int].run(Left(6))
        v2 <- succeedLazy(1).left[String].run(Right("hi"))
      } yield (v1 must beLeft(12)) and (v2 must beRight("hi"))
    )

  def e11 =
    unsafeRun(
      for {
        v1 <- lift[Int, Int](_ * 2).right[String].run(Left("no value"))
        v2 <- lift[Int, Int](_ * 2).right[Int].run(Right(7))
      } yield (v1 must beLeft("no value")) and (v2 must beRight(14))
    )

  def e12 =
    unsafeRun(
      for {
        v <- lift[Int, Int](_ * 2).asEffect.run(56)
      } yield v must_=== 56
    )

  def e13 =
    unsafeRun(
      for {
        v1 <- FunctionIO.test(lift[Array[Int], Boolean](_.sum > 10)).run(Array(1, 2, 5))
        v2 <- FunctionIO.test(lift[Array[Int], Boolean](_.sum > 10)).run(Array(1, 2, 5, 6))
      } yield (v1 must beRight(Array(1, 2, 5))) and (v2 must beLeft(Array(1, 2, 5, 6)))
    )

  def e14a =
    unsafeRun(
      for {
        v1 <- ifThenElse(lift[Int, Boolean](_ > 0))(succeedLazy("is positive"))(
               succeedLazy("is negative")
             ).run(-1)
        v2 <- ifThenElse(lift[Int, Boolean](_ > 0))(succeedLazy("is positive"))(
               succeedLazy("is negative")
             ).run(1)
      } yield (v1 must_=== "is negative") and (v2 must_=== "is positive")
    )

  def e14b =
    unsafeRun(
      for {
        v1 <- ifThenElse(pure[Nothing, Int, Boolean](a => IO.succeed(a > 0)))(succeedLazy("is positive"))(
               succeedLazy("is negative")
             ).run(-1)
        v2 <- ifThenElse(pure[Nothing, Int, Boolean](a => IO.succeed(a > 0)))(succeedLazy("is positive"))(
               succeedLazy("is negative")
             ).run(1)
      } yield (v1 must_=== "is negative") and (v2 must_=== "is positive")
    )

  def e15a =
    unsafeRun(
      for {
        v <- whileDo[Nothing, Int](lift[Int, Boolean](_ < 10))(lift[Int, Int](_ + 1)).run(1)
      } yield v must_=== 10
    )

  def e15b =
    unsafeRun(
      for {
        v <- whileDo[Nothing, Int](pure[Nothing, Int, Boolean](a => IO.succeed[Boolean](a < 10)))(
              pure[Nothing, Int, Int](a => IO.defer[Int](a + 1))
            ).run(1)
      } yield v must_=== 10
    )

  def e16 =
    unsafeRun(
      for {
        v <- _1[Nothing, Int, String].run((1, "hi"))
      } yield v must_=== 1
    )

  def e17 =
    unsafeRun(
      for {
        v <- _2[Nothing, Int, String].run((2, "hola"))
      } yield v must_=== "hola"
    )

  def e18a =
    unsafeRun(
      for {
        a <- fail[String]("error").run(1).attempt
      } yield a must_=== Left("error")
    )

  def e18b =
    unsafeRun(
      for {
        a <- impure[String, Int, Int] { case _: Throwable => "error" }(_ => throw new Exception).run(9).attempt
      } yield a must_=== Left("error")
    )
}
