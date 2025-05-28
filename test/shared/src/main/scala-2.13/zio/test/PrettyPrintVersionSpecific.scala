package zio.test

import zio.internal.ansi.AnsiStringOps
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[test] trait PrettyPrintVersionSpecific {

  def prettyPrintProduct(product: Product): String = {
    val name = product.productPrefix
    val size = product.productArity

    if (size < 1) s"$name()"
    else {
      val isMultiLine = size > 1
      val indentation = if (isMultiLine) "  " else ""

      val acc = new Array[String](size)

      // First line handling
      val key0            = product.productElementName(0)
      val value0          = product.productElement(0)
      val firstLineSuffix = if (isMultiLine) ',' else ""
      val firstLine       = s"$indentation${(key0 + " =").faint} ${PrettyPrint(value0)}$firstLineSuffix"
      acc(0) = firstLine

      // Remaining lines handling
      var i           = 1
      val lastElement = size - 1
      while (i < size) {
        val key        = product.productElementName(i)
        val value      = product.productElement(i)
        val isLastLine = i == lastElement
        val suffix     = if (isLastLine) "" else ","
        acc(i) = s"\n$indentation${(key + " =").faint} ${PrettyPrint(value)}$suffix"
        i += 1
      }

      // Final result formatting
      val body   = acc.mkString
      val spacer = if (isMultiLine) '\n' else ""
      s"""$name($spacer$body$spacer)"""
    }
  }
}
