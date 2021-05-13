package zio.test

import zio.Chunk
import zio.test.AssertionSyntax.AssertionOps
import zio.test.SmartAssertionIsolatedTest.{BadError, CoolError}
import zio.test.SmartTestTypes._

/**
 * - Scala 3
 * - Refactor to make Scala 3 easy.
 * - Create some data structure that allows us to list out
 *   - Method Name, Method
 * - Fix up rendering issues.
 *   √ Do not list value for literals
 *   √ Add spaces around infix with code show.
 *   √ Special case apply.
 *   √ Use the actual written showCode(expr) for the withField for the macro code
 *   - Fix IsConstructor
 * √ Improve rendering for all the existing assertions
 * √ conjunction/disjunction/negation, break apart at top level in macro.
 * √ handle exceptions thrown by methods in `assert`
 * √ Add a prose, human-readable error message for assertions (instead of 'does not satisfy hasAt(0)')
 * - Add all the methods we want
 *   - right.get (on an Either)
 *   - toOption.get (on an Either)
 *   - forall
 * - Diff Stuff. Strings, case classes, maps, anything. User customizable.
 * - Exposing bugs. try to break in as many ways as possible, and add helpful error messages
 *   √ how to handle multi-statement blocks
 */

object SmartAssertionIsolatedTest extends ZIOBaseSpec {

  val company: Company = Company("Ziverge", List(User("Bobo", List.tabulate(2)(n => Post(s"Post #$n")))))

  sealed trait NiceError extends Throwable

  case class CoolError() extends NiceError
  case class BadError()  extends NiceError

  def spec: ZSpec[Annotations, Any] = suite("SmartAssertionSpec")(
    test("filterConstFalseResultsInEmptyChunk") {
      AssertExamples.main(Array())
      assertCompletes
    }
  ) @@ TestAspect.identity

}

object AssertExamples {
  //  def optionExample = {
  //    val option = Assert.succeed(Option.empty[Int]).label("maybeInt") >>>
  //      Assert.get[Int] >>> Assert.fromFunction((_: Int) > 10).label(" > 10")
  //    Assert.run(option, Right(()))
  //  }
  //
  //  lazy val throwingExample = {
  //    val a = Assert.succeed[Int](10).label("10") >>>
  //      Assert.fromFunction((_: Int) + 10).label(" + 10") >>>
  //      Assert.fromFunction((_: Any) => throw new Error("BANG")).label("BOOM") >>>
  //      Assert.fromFunction((_: Int) % 2 == 0).label(" % 2 == 0") >>>
  //      Assert.fromFunction((_: Boolean) == true).label(" == true") >>>
  //      Assert.throws
  //
  //    Assert.run(a, Right(10))
  //  }

  lazy val booleanLogic = {
    val ten   = 10
    val hello = "hello"

    case class Person(age: Int, name: String, nickname: Option[String]) {
      def boom: Int = throw new Error("BOOM")
    }

    val person = Person(42, "Bobby", None)

    val a = assertZoom(person.nickname.get == "Bill") // || person.name == "Bobby")
//    val b         = assertZoom(ten > 11 || hello.length > 8 || hello.length > 100)
    val assertion = a // && b

    var result = Assert.run(assertion, Right(()))
    result = Trace.prune(result, false).getOrElse(Trace.Node(Result.Succeed(true)))
    result
  }

  def main(args: Array[String]): Unit = {
    val result = booleanLogic
//    println(result)
//    println("")
//    println(Pretty(result))
//    println("")

//    val tree = Tree.fromTrace(result)
//    println(Pretty(tree))
//    println("")
//    println(tree.render)

    val failure = FailureCase.fromTrace(result)
    println("")
    println(
      failure.map { f =>
        FailureCase.renderFailureCase(f).mkString("\n")
      }.mkString("\n\n")
    )
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
