package zio.test

object AssertExamples {
  def optionExample = {
    val option = Assert.succeed(Option.empty[Int]).label("maybeInt") >>>
      Assertions.get[Int] >>> Assert.fromFunction((_: Int) > 10).label(" > 10")
    Assert.run(option, Right(()))
  }

  val throwingExample = {
    val a = Assert.succeed[Int](10) >>>
      Assert.fromFunction((_: Int) + 10).label(" + 10") >>>
      Assert.fromFunction((_: Any) => throw new Error("BANG")).label("BOOM") >>>
      Assert.fromFunction((_: Int) % 2 == 0).label(" % 2 == 0") >>>
      Assert.fromFunction((_: Boolean) == true).label(" == true") >>>
      Assertions.throws

    Assert.run(a, Right(10))
  }

  val booleanLogic = {
    val a      = Assert.succeed(10) >>> Assert.fromFunction(_ > 11)
    val b      = Assert.succeed("hello") >>> Assert.fromFunction(_.isEmpty)
    val c      = Assert.succeed(Some(12)) >>> Assertions.get >>> Assert.fromFunction(_ == 10)
    val result = a && b && c
    Assert.run(result, Right(()))
  }

  def main(args: Array[String]): Unit = {
    val result = booleanLogic
    println(result)
    println("")
    result.debug
    println("")
    val tree = Tree.fromTrace(result)

    case class Succeed(any: Any)

    println(tree)
    println(tree.render)
  }
}
