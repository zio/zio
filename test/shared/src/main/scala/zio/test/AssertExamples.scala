package zio.test

object AssertExamples {
  def optionExample = {
    val option = Assert.succeed(Option.empty[Int]).label("maybeInt") >>>
      Assert.get[Int] >>> Assert.fromFunction((_: Int) > 10).label(" > 10")
    Assert.run(option, Right(()))
  }

  val throwingExample = {
    val a = Assert.succeed[Int](10).label("10") >>>
      Assert.fromFunction((_: Int) + 10).label(" + 10") >>>
      Assert.fromFunction((_: Any) => throw new Error("BANG")).label("BOOM") >>>
      Assert.fromFunction((_: Int) % 2 == 0).label(" % 2 == 0") >>>
      Assert.fromFunction((_: Boolean) == true).label(" == true") >>>
      Assert.throws

    Assert.run(a, Right(10))
  }

  val booleanLogic = {
//    val a      = Assert.succeed(10) >>> Assert.fromFunction(_ > 11)
//    val b      = Assert.succeed("hello") >>> Assert.fromFunction(_.isEmpty)
//    val c      = Assert.succeed(Some(12)) >>> Assertions.get >>> Assert.fromFunction(_ == 10)
//    val result = a && b && c
    val ten   = 10
    val hello = "hello"

    val assert = assertZoom(ten > 19 || hello.length == 5)
    val trace  = Assert.run(assert, Right(()))
    Trace.markFailures(trace, false)
  }

  def main(args: Array[String]): Unit = {
    val result = booleanLogic
    println(result)
    println("")
    println(Pretty(result))
    println("")
    val tree = Tree.fromTrace(result)

    case class Succeed(any: Any)

    println(Pretty(tree))
    println("")
    println(tree.render)
  }
}

object Pretty {

  /**
   * Pretty prints a Scala value similar to its source represention.
   * Particularly useful for case classes.
   * @param a - The value to pretty print.
   * @param indentSize - Number of spaces for each indent.
   * @param maxElementWidth - Largest element size before wrapping.
   * @param depth - Initial depth to pretty print indents.
   * @return
   */
  def apply(a: Any, indentSize: Int = 2, maxElementWidth: Int = 30, depth: Int = 0): String = {
    val indent      = " " * depth * indentSize
    val fieldIndent = indent + (" " * indentSize)
    val thisDepth   = apply(_: Any, indentSize, maxElementWidth, depth)
    val nextDepth   = apply(_: Any, indentSize, maxElementWidth, depth + 1)
    a match {
      // Make Strings look similar to their literal form.
      case s: String =>
        val replaceMap = Seq(
          "\n" -> "\\n",
          "\r" -> "\\r",
          "\t" -> "\\t",
          "\"" -> "\\\""
        )
        '"' + replaceMap.foldLeft(s) { case (acc, (c, r)) => acc.replace(c, r) } + '"'
      // For an empty Seq just use its normal String representation.
      case xs: Seq[_] if xs.isEmpty => xs.toString()
      case xs: Seq[_]               =>
        // If the Seq is not too long, pretty print on one line.
        val resultOneLine = xs.map(nextDepth).toString()
        if (resultOneLine.length <= maxElementWidth) return resultOneLine
        // Otherwise, build it with newlines and proper field indents.
        val result = xs.map(x => s"\n$fieldIndent${nextDepth(x)}").toString()
        result.substring(0, result.length - 1) + "\n" + indent + ")"
      // Product should cover case classes.
      case p: Product =>
        val prefix = p.productPrefix
        // We'll use reflection to get the constructor arg names and values.
        val cls    = p.getClass
        val fields = cls.getDeclaredFields.filterNot(_.isSynthetic).map(_.getName)
        val values = p.productIterator.toSeq
        // If we weren't able to match up fields/values, fall back to toString.
        if (fields.length != values.length) return p.toString
        fields.zip(values).toList match {
          // If there are no fields, just use the normal String representation.
          case Nil => p.toString
          // If there is just one field, let's just print it as a wrapper.
          case (_, value) :: Nil => s"$prefix(${thisDepth(value)})"
          // If there is more than one field, build up the field names and values.
          case kvps =>
            val prettyFields = kvps.map { case (k, v) => s"$fieldIndent$k = ${nextDepth(v)}" }
            // If the result is not too long, pretty print on one line.
            val resultOneLine = s"$prefix(${prettyFields.mkString(", ")})"
            if (resultOneLine.length <= maxElementWidth) return resultOneLine
            // Otherwise, build it with newlines and proper field indents.
            s"$prefix(\n${prettyFields.mkString(",\n")}\n$indent)"
        }
      // If we haven't specialized this type, just use its toString.
      case _ => a.toString
    }
  }
}
