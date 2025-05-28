package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace

private[test] trait PrettyPrintVersionSpecific {

  def prettyPrintProduct(product: Product): String = {
    val name = product.productPrefix
    val size = product.productArity

    if (size < 1) s"$name()"
    else {
      val isMultiLine = size > 1
      val indentation = if (isMultiLine) "  " else ""
      val spacer      = if (isMultiLine) '\n' else ""

      val acc = new java.lang.StringBuilder

      acc.append(name)
      acc.append('(')
      acc.append(spacer)

      // First line handling
      val key0            = product.productElementName(0)
      val value0          = product.productElement(0)
      val firstLineSuffix = if (isMultiLine) ',' else ""
      acc.append(indentation)
      acc.append(key0)
      acc.append(" = ")
      acc.append(PrettyPrint(value0))
      acc.append(firstLineSuffix)

      // Remaining lines handling
      var i           = 1
      val lastElement = size - 1
      while (i < size) {
        val key        = product.productElementName(i)
        val value      = product.productElement(i)
        val isLastLine = i == lastElement
        val suffix     = if (isLastLine) "" else ','
        acc.append('\n')
        acc.append(indentation)
        acc.append(key)
        acc.append(" = ")
        acc.append(PrettyPrint(value))
        acc.append(suffix)

        i += 1
      }

      acc.append(spacer)
      acc.append(')')
      acc.toString
    }
  }
}
