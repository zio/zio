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

      // format: off
      case tuple1: Tuple1[?] =>
        s"(${PrettyPrint(tuple1._1)})"
      case tuple2: (?, ?) =>
        s"(${PrettyPrint(tuple2._1)}, ${PrettyPrint(tuple2._2)})"
      case tuple3: (?, ?, ?) =>
        s"(${PrettyPrint(tuple3._1)}, ${PrettyPrint(tuple3._2)}, ${PrettyPrint(tuple3._3)})"
      case tuple4: (?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple4._1)}, ${PrettyPrint(tuple4._2)}, ${PrettyPrint(tuple4._3)}, ${PrettyPrint(tuple4._4)})"
      case tuple5: (?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple5._1)}, ${PrettyPrint(tuple5._2)}, ${PrettyPrint(tuple5._3)}, ${PrettyPrint(tuple5._4)}, ${PrettyPrint(tuple5._5)})"
      case tuple6: (?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple6._1)}, ${PrettyPrint(tuple6._2)}, ${PrettyPrint(tuple6._3)}, ${PrettyPrint(tuple6._4)}, ${PrettyPrint(tuple6._5)}, ${PrettyPrint(tuple6._6)})"
      case tuple7: (?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple7._1)}, ${PrettyPrint(tuple7._2)}, ${PrettyPrint(tuple7._3)}, ${PrettyPrint(tuple7._4)}, ${PrettyPrint(tuple7._5)}, ${PrettyPrint(tuple7._6)}, ${PrettyPrint(tuple7._7)})"
      case tuple8: (?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple8._1)}, ${PrettyPrint(tuple8._2)}, ${PrettyPrint(tuple8._3)}, ${PrettyPrint(tuple8._4)}, ${PrettyPrint(tuple8._5)}, ${PrettyPrint(tuple8._6)}, ${PrettyPrint(tuple8._7)}, ${PrettyPrint(tuple8._8)})"
      case tuple9: (?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple9._1)}, ${PrettyPrint(tuple9._2)}, ${PrettyPrint(tuple9._3)}, ${PrettyPrint(tuple9._4)}, ${PrettyPrint(tuple9._5)}, ${PrettyPrint(tuple9._6)}, ${PrettyPrint(tuple9._7)}, ${PrettyPrint(tuple9._8)}, ${PrettyPrint(tuple9._9)})"
      case tuple10: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple10._1)}, ${PrettyPrint(tuple10._2)}, ${PrettyPrint(tuple10._3)}, ${PrettyPrint(tuple10._4)}, ${PrettyPrint(tuple10._5)}, ${PrettyPrint(tuple10._6)}, ${PrettyPrint(tuple10._7)}, ${PrettyPrint(tuple10._8)}, ${PrettyPrint(tuple10._9)}, ${PrettyPrint(tuple10._10)})"
      case tuple11: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple11._1)}, ${PrettyPrint(tuple11._2)}, ${PrettyPrint(tuple11._3)}, ${PrettyPrint(tuple11._4)}, ${PrettyPrint(tuple11._5)}, ${PrettyPrint(tuple11._6)}, ${PrettyPrint(tuple11._7)}, ${PrettyPrint(tuple11._8)}, ${PrettyPrint(tuple11._9)}, ${PrettyPrint(tuple11._10)}, ${PrettyPrint(tuple11._11)})"
      case tuple12: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple12._1)}, ${PrettyPrint(tuple12._2)}, ${PrettyPrint(tuple12._3)}, ${PrettyPrint(tuple12._4)}, ${PrettyPrint(tuple12._5)}, ${PrettyPrint(tuple12._6)}, ${PrettyPrint(tuple12._7)}, ${PrettyPrint(tuple12._8)}, ${PrettyPrint(tuple12._9)}, ${PrettyPrint(tuple12._10)}, ${PrettyPrint(tuple12._11)}, ${PrettyPrint(tuple12._12)})"
      case tuple13: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple13._1)}, ${PrettyPrint(tuple13._2)}, ${PrettyPrint(tuple13._3)}, ${PrettyPrint(tuple13._4)}, ${PrettyPrint(tuple13._5)}, ${PrettyPrint(tuple13._6)}, ${PrettyPrint(tuple13._7)}, ${PrettyPrint(tuple13._8)}, ${PrettyPrint(tuple13._9)}, ${PrettyPrint(tuple13._10)}, ${PrettyPrint(tuple13._11)}, ${PrettyPrint(tuple13._12)}, ${PrettyPrint(tuple13._13)})"
      case tuple14: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple14._1)}, ${PrettyPrint(tuple14._2)}, ${PrettyPrint(tuple14._3)}, ${PrettyPrint(tuple14._4)}, ${PrettyPrint(tuple14._5)}, ${PrettyPrint(tuple14._6)}, ${PrettyPrint(tuple14._7)}, ${PrettyPrint(tuple14._8)}, ${PrettyPrint(tuple14._9)}, ${PrettyPrint(tuple14._10)}, ${PrettyPrint(tuple14._11)}, ${PrettyPrint(tuple14._12)}, ${PrettyPrint(tuple14._13)}, ${PrettyPrint(tuple14._14)})"
      case tuple15: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple15._1)}, ${PrettyPrint(tuple15._2)}, ${PrettyPrint(tuple15._3)}, ${PrettyPrint(tuple15._4)}, ${PrettyPrint(tuple15._5)}, ${PrettyPrint(tuple15._6)}, ${PrettyPrint(tuple15._7)}, ${PrettyPrint(tuple15._8)}, ${PrettyPrint(tuple15._9)}, ${PrettyPrint(tuple15._10)}, ${PrettyPrint(tuple15._11)}, ${PrettyPrint(tuple15._12)}, ${PrettyPrint(tuple15._13)}, ${PrettyPrint(tuple15._14)}, ${PrettyPrint(tuple15._15)})"
      case tuple16: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple16._1)}, ${PrettyPrint(tuple16._2)}, ${PrettyPrint(tuple16._3)}, ${PrettyPrint(tuple16._4)}, ${PrettyPrint(tuple16._5)}, ${PrettyPrint(tuple16._6)}, ${PrettyPrint(tuple16._7)}, ${PrettyPrint(tuple16._8)}, ${PrettyPrint(tuple16._9)}, ${PrettyPrint(tuple16._10)}, ${PrettyPrint(tuple16._11)}, ${PrettyPrint(tuple16._12)}, ${PrettyPrint(tuple16._13)}, ${PrettyPrint(tuple16._14)}, ${PrettyPrint(tuple16._15)}, ${PrettyPrint(tuple16._16)})"
      case tuple17: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple17._1)}, ${PrettyPrint(tuple17._2)}, ${PrettyPrint(tuple17._3)}, ${PrettyPrint(tuple17._4)}, ${PrettyPrint(tuple17._5)}, ${PrettyPrint(tuple17._6)}, ${PrettyPrint(tuple17._7)}, ${PrettyPrint(tuple17._8)}, ${PrettyPrint(tuple17._9)}, ${PrettyPrint(tuple17._10)}, ${PrettyPrint(tuple17._11)}, ${PrettyPrint(tuple17._12)}, ${PrettyPrint(tuple17._13)}, ${PrettyPrint(tuple17._14)}, ${PrettyPrint(tuple17._15)}, ${PrettyPrint(tuple17._16)}, ${PrettyPrint(tuple17._17)})"
      case tuple18: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple18._1)}, ${PrettyPrint(tuple18._2)}, ${PrettyPrint(tuple18._3)}, ${PrettyPrint(tuple18._4)}, ${PrettyPrint(tuple18._5)}, ${PrettyPrint(tuple18._6)}, ${PrettyPrint(tuple18._7)}, ${PrettyPrint(tuple18._8)}, ${PrettyPrint(tuple18._9)}, ${PrettyPrint(tuple18._10)}, ${PrettyPrint(tuple18._11)}, ${PrettyPrint(tuple18._12)}, ${PrettyPrint(tuple18._13)}, ${PrettyPrint(tuple18._14)}, ${PrettyPrint(tuple18._15)}, ${PrettyPrint(tuple18._16)}, ${PrettyPrint(tuple18._17)}, ${PrettyPrint(tuple18._18)})"
      case tuple19: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple19._1)}, ${PrettyPrint(tuple19._2)}, ${PrettyPrint(tuple19._3)}, ${PrettyPrint(tuple19._4)}, ${PrettyPrint(tuple19._5)}, ${PrettyPrint(tuple19._6)}, ${PrettyPrint(tuple19._7)}, ${PrettyPrint(tuple19._8)}, ${PrettyPrint(tuple19._9)}, ${PrettyPrint(tuple19._10)}, ${PrettyPrint(tuple19._11)}, ${PrettyPrint(tuple19._12)}, ${PrettyPrint(tuple19._13)}, ${PrettyPrint(tuple19._14)}, ${PrettyPrint(tuple19._15)}, ${PrettyPrint(tuple19._16)}, ${PrettyPrint(tuple19._17)}, ${PrettyPrint(tuple19._18)}, ${PrettyPrint(tuple19._19)})"
      case tuple20: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple20._1)}, ${PrettyPrint(tuple20._2)}, ${PrettyPrint(tuple20._3)}, ${PrettyPrint(tuple20._4)}, ${PrettyPrint(tuple20._5)}, ${PrettyPrint(tuple20._6)}, ${PrettyPrint(tuple20._7)}, ${PrettyPrint(tuple20._8)}, ${PrettyPrint(tuple20._9)}, ${PrettyPrint(tuple20._10)}, ${PrettyPrint(tuple20._11)}, ${PrettyPrint(tuple20._12)}, ${PrettyPrint(tuple20._13)}, ${PrettyPrint(tuple20._14)}, ${PrettyPrint(tuple20._15)}, ${PrettyPrint(tuple20._16)}, ${PrettyPrint(tuple20._17)}, ${PrettyPrint(tuple20._18)}, ${PrettyPrint(tuple20._19)}, ${PrettyPrint(tuple20._20)})"
      case tuple21: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple21._1)}, ${PrettyPrint(tuple21._2)}, ${PrettyPrint(tuple21._3)}, ${PrettyPrint(tuple21._4)}, ${PrettyPrint(tuple21._5)}, ${PrettyPrint(tuple21._6)}, ${PrettyPrint(tuple21._7)}, ${PrettyPrint(tuple21._8)}, ${PrettyPrint(tuple21._9)}, ${PrettyPrint(tuple21._10)}, ${PrettyPrint(tuple21._11)}, ${PrettyPrint(tuple21._12)}, ${PrettyPrint(tuple21._13)}, ${PrettyPrint(tuple21._14)}, ${PrettyPrint(tuple21._15)}, ${PrettyPrint(tuple21._16)}, ${PrettyPrint(tuple21._17)}, ${PrettyPrint(tuple21._18)}, ${PrettyPrint(tuple21._19)}, ${PrettyPrint(tuple21._20)}, ${PrettyPrint(tuple21._21)})"
      case tuple22: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) =>
        s"(${PrettyPrint(tuple22._1)}, ${PrettyPrint(tuple22._2)}, ${PrettyPrint(tuple22._3)}, ${PrettyPrint(tuple22._4)}, ${PrettyPrint(tuple22._5)}, ${PrettyPrint(tuple22._6)}, ${PrettyPrint(tuple22._7)}, ${PrettyPrint(tuple22._8)}, ${PrettyPrint(tuple22._9)}, ${PrettyPrint(tuple22._10)}, ${PrettyPrint(tuple22._11)}, ${PrettyPrint(tuple22._12)}, ${PrettyPrint(tuple22._13)}, ${PrettyPrint(tuple22._14)}, ${PrettyPrint(tuple22._15)}, ${PrettyPrint(tuple22._16)}, ${PrettyPrint(tuple22._17)}, ${PrettyPrint(tuple22._18)}, ${PrettyPrint(tuple22._19)}, ${PrettyPrint(tuple22._20)}, ${PrettyPrint(tuple22._21)}, ${PrettyPrint(tuple22._22)})"
      // format: on

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
