package zio

class OptionCombinatorsSpec extends BaseCrossPlatformSpec {
  def is = "OptionCombinatorsSpec".title ^ s2"""
    Operate on optional values
      sum two optional values $optionalValues
      sum two optional values when one is None $optionalValues2
      fail when summed with a failed value $optionalValues3
    """

  def optionalValues = {
    val value1: Task[Option[Int]] = Task(Some(1))
    val value2: Task[Option[Int]] = Task(Some(2))

    val result: IO[Option[Throwable], Int] = for {
      val1 <- value1.some
      val2 <- value2.some
    } yield val1 + val2
        
    result.peelError must_=== Some(3)
  }

  def optionalValues2 = {
    val value1: Task[Option[Int]] = Task(Some(1))
    val value2: Task[Option[Int]] = Task(None)

    val result: IO[Option[Throwable], Int] = for {
      val1 <- value1.some
      val2 <- value2.some
    } yield val1 + val2
        
    result.peelError must_=== None
  }

  def optionalValues3 = {
    val ex = new RuntimeException("Failed Task")
    val value1: Task[Option[Int]] = Task(Some(1))
    val value2: Task[Option[Int]] = Task.fail(ex)

    val result: IO[Option[Throwable], Int] = for {
      val1 <- value1.some
      val2 <- value2.some
    } yield val1 + val2

    result.peelError mustFailBecauseOf Cause.fail(ex)
  }
}
