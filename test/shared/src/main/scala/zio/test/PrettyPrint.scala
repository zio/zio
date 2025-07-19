package zio.test

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
      case null => "<null>"

      case string: String =>
        val surround = if (string.contains('\n')) "\"\"\"" else "\""
        // `+ 16` to take into account the potientials `\\` added when there are some `\"` characters
        val builder = new java.lang.StringBuilder(string.length + 2 * surround.length + 16)
        builder.append(surround)
        builder.append(string.replace("\"", """\""""))
        builder.append(surround)
        builder.toString

      case int: Int         => String.valueOf(int)
      case long: Long       => String.valueOf(long)
      case double: Double   => String.valueOf(double)
      case float: Float     => String.valueOf(float)
      case boolean: Boolean => String.valueOf(boolean)
      case char: Char =>
        val s = new Array[Char](3)
        s(0) = '\''
        s(1) = char
        s(2) = '\''
        new String(s)
      case short: Short           => String.valueOf(short)
      case byte: Byte             => String.valueOf(byte)
      case bigDecimal: BigDecimal => bigDecimal.toString
      case bigInt: BigInt         => bigInt.toString
      case symbol: Symbol         => symbol.toString

      case Some(a) => s"Some(${PrettyPrint(a)})"
      case None    => "None"

      // For why `Nil.type` is used. See https://github.com/zio/zio/pull/9900#discussion_r2121380398
      case _: Nil.type => "Nil"

      case chunk: Chunk[_]                 => prettyPrintIterator(chunk, "Chunk")
      case list: List[_]                   => prettyPrintIterator(list, "List")
      case vector: Vector[_]               => prettyPrintIterator(vector, "Vector")
      case array: Array[_]                 => prettyPrintIterator(array, "Array")
      case set: Set[_]                     => prettyPrintIterator(set, "Set")
      case nonEmptyChunk: NonEmptyChunk[_] => prettyPrintIterator(nonEmptyChunk, "NonEmptyChunk")
      case iterable: Seq[_]                => prettyPrintIterator(iterable, className(iterable))

      case map: Map[_, _] =>
        val body = map.map { case (key, value) => s"${PrettyPrint(key)} -> ${PrettyPrint(value)}" }
        s"""Map(
${indent(body.mkString(",\n"))}
)"""

      case product: Product => prettyPrintProduct(product)

      case other => other.toString
    }

  private def prettyPrintIterator(iterable: Iterable[_], className: String): String =
    if (iterable.isEmpty) s"$className()"
    else {
      val acc = new java.lang.StringBuilder(className.length + 16 * iterable.size)
      acc.append(className)
      acc.append('(')
      val iterator = iterable.iterator
      acc.append(PrettyPrint(iterator.next))
      while (iterator.hasNext) {
        acc.append(", ")
        acc.append(PrettyPrint(iterator.next))
      }
      acc.append(')')
      acc.toString
    }

  private def indent(string: String): String = string.split("\n").map(v => s"  $v").mkString("\n")

  private def className(any: Any): String = any.getClass.getSimpleName

}
