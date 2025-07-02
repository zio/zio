package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace

private[test] trait PrettyPrintVersionSpecific {

  def prettyPrintProduct(product: Product): String = {
    val name = product.productPrefix
    val size = product.productArity

    if (size < 1) s"$name()"
    else {
      val isMultiLine = size > 1

      val acc = new java.lang.StringBuilder(
        name.length + 16 * size + (if (isMultiLine) 8 * size else 0)
      )

      acc.append(name)
      acc.append('(')
      if (isMultiLine) acc.append('\n')

      val key0   = product.productElementName(0)
      val value0 = product.productElement(0)
      if (isMultiLine) acc.append("  ") // indentation
      acc.append(key0)
      acc.append(" = ")
      acc.append(PrettyPrint(value0))
      if (isMultiLine) acc.append(',')

      var i           = 1
      val lastElement = size - 1
      while (i < size) {
        val key         = product.productElementName(i)
        val value       = product.productElement(i)
        val notLastLine = i != lastElement
        acc.append('\n')
        acc.append("  ") // indentation
        acc.append(key)
        acc.append(" = ")
        acc.append(PrettyPrint(value))
        if (notLastLine) acc.append(',')

        i += 1
      }

      if (isMultiLine) acc.append('\n')
      acc.append(')')
      acc.toString
    }
  }
}
