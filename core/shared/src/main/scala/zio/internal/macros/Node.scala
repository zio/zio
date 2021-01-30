package zio.internal.macros

case class Node[+A](inputs: List[String], outputs: List[String], value: A)
