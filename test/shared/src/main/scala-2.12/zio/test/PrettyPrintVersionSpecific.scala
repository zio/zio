package zio.test

import zio.internal.ansi.AnsiStringOps
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[test] trait PrettyPrintVersionSpecific {
  private def labels(product: Product): Iterator[String] = {
    val _ = product
    Iterator.continually("")
  }

  def prettyPrintProduct(product: Product): String = {
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
  }

  private def indent(string: String, n: Int = 2): String = string.split("\n").map((" " * n) + _).mkString("\n")
}
