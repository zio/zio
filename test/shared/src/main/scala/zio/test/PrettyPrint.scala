package zio.test

import zio.internal.ansi.AnsiStringOps
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Chunk, NonEmptyChunk}

import scala.annotation.switch

/**
 * PrettyPrint will attempt to render a Scala value as the syntax used to create
 * that value. This makes it easier to copy-paste from values printed to the
 * console during tests back into runnable code.
 */
private[zio] object PrettyPrint extends PrettyPrintVersionSpecific {
  def apply(any: Any): String =
    (any: @switch) match {
      case null    => "<null>"
      case _: Unit => "()" // Unit is printed as empty parentheses

      case string: String =>
        val surround = if (string.contains('\n')) "\"\"\"" else "\""
        string.replace("\"", """\"""").mkString(surround, "", surround)

      case int: Int               => int.toString
      case long: Long             => long.toString
      case double: Double         => double.toString
      case float: Float           => float.toString
      case boolean: Boolean       => boolean.toString
      case char: Char             => s"'${char.toString}'"
      case short: Short           => short.toString
      case byte: Byte             => byte.toString
      case bigDecimal: BigDecimal => bigDecimal.toString
      case bigInt: BigInt         => bigInt.toString
      case symbol: Symbol         => symbol.toString

      case Some(a) => s"Some(${PrettyPrint(a)})"
      case None    => s"None"
      case Nil     => "Nil"

      case chunk: Chunk[_]                 => prettyPrintIterator(chunk, "Chunk")
      case list: List[_]                   => prettyPrintIterator(list, "List")
      case vector: Vector[_]               => prettyPrintIterator(vector, "Vector")
      case array: Array[_]                 => prettyPrintIterator(array, "Array")
      case set: Set[_]                     => prettyPrintIterator(set, className(set))
      case nonEmptyChunk: NonEmptyChunk[_] => prettyPrintIterator(nonEmptyChunk, "NonEmptyChunk")
      case iterable: Seq[_]                => prettyPrintIterator(iterable, className(iterable))

      case map: Map[_, _] =>
        val body = map.map { case (key, value) => s"${PrettyPrint(key)} -> ${PrettyPrint(value)}" }
        s"""Map(
${indent(body.mkString(",\n"))}
)"""

      case product: Product =>
        val name    = product.productPrefix
        val labels0 = labels(product)
        val body = labels0
          .zip(product.productIterator)
          .map { case (key, value) =>
            s"${(key + " =").faint} ${PrettyPrint(value)}"
          }
          .toList
          .mkString(",\n")
        val isMultiline  = body.split("\n").length > 1
        val indentedBody = indent(body, if (isMultiline) 2 else 0)
        val spacer       = if (isMultiline) "\n" else ""
        s"""$name($spacer$indentedBody$spacer)"""

      case other => other.toString
    }

  private def prettyPrintIterator(iterable: Iterable[_], className: String): String =
    if (iterable.isEmpty) s"$className()"
    else {
      val builder = new StringBuilder(iterable.size + 2)
      builder.append(s"$className(")
      val iterator = iterable.iterator
      builder.append(s"${PrettyPrint.apply(iterator.next)}")
      while (iterator.hasNext) {
        builder.append(s", ${PrettyPrint.apply(iterator.next)}")
      }
      builder.append(")")
      builder.result()
    }

  private def indent(string: String, n: Int = 2): String =
    string.split("\n").map((" " * n) + _).mkString("\n")

  private def className(any: Any): String = any.getClass.getSimpleName

}
