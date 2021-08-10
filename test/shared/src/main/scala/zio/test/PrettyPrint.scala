package zio.test

/**
 * PrettyPrint will attempt to render a Scala value as the syntax used to create
 * that value. This makes it easier to copy-paste from values printed to the
 * console during tests back into runnable code.
 */
private[zio] object PrettyPrint extends PrettyPrintVersionSpecific {
  def apply(any: Any): String = any match {
    case array: Array[_] =>
      array.map(PrettyPrint.apply).mkString("Array(", ", ", ")")

    case iterable: Seq[_] =>
      iterable.map(PrettyPrint.apply).mkString(s"${className(iterable)}(", ", ", ")")

    case map: Map[_, _] =>
      val body = map.map { case (key, value) => s"${PrettyPrint(key)} -> ${PrettyPrint(value)}" }
      s"""Map(
${indent(body.mkString(",\n"))}
)"""

    case product: Product =>
      val name = product.productPrefix
      val body = labels(product).zip(product.productIterator).map { case (key, value) =>
        s"$key = ${PrettyPrint(value)}"
      }
      s"""$name(
${indent(body.mkString(",\n"))}
)"""

    case string: String =>
      string.replace("\"", """\"""").mkString("\"", "", "\"")

    case other => other.toString
  }

  private def indent(string: String, n: Int = 2): String =
    string.split("\n").map((" " * n) + _).mkString("\n")

  private def className(any: Any): String = any match {
    case _: List[_] => "List"
    case other      => other.getClass.getSimpleName
  }

}
