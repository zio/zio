package zio.test

import zio.{Chunk, ZIO}

import scala.annotation.tailrec
import scala.reflect.macros.whitebox

class SmartSpecMacros(val c: whitebox.Context) {
  import c.universe._

  sealed trait TestOrStatement extends Product with Serializable

  object TestOrStatement {
    final case class Test(tree: Tree)      extends TestOrStatement
    final case class Statement(tree: Tree) extends TestOrStatement
  }

  def collectTests(tree: Tree): List[TestOrStatement] =
    tree match {
      case Block(statements, finalStatement) =>
        statements.flatMap(collectTests) ++ collectTests(finalStatement)

      case test if test.tpe <:< c.weakTypeOf[Spec[_, _]] =>
        List(TestOrStatement.Test(test))

      case test if test.tpe <:< c.weakTypeOf[ZIO[_, _, Chunk[Spec[_, _]]]] =>
        List(TestOrStatement.Test(test))

      case test if test.tpe <:< c.weakTypeOf[Chunk[Spec[_, _]]] =>
        List(TestOrStatement.Test(test))

      case other =>
        List(TestOrStatement.Statement(other))
    }

  def suiteImpl(name: c.Expr[String])(spec: c.Tree): c.Tree = {
    val result = collectTests(spec)

    @tailrec
    def loop(remaining: List[TestOrStatement], acc: List[Tree], names: List[String]): Tree =
      remaining match {
        case head :: tail =>
          head match {
            case TestOrStatement.Test(tree) =>
              val newName  = c.freshName("test")
              val newNames = newName :: names
              val newTree  = q"val ${TermName(newName)} = $tree"
              loop(tail, newTree :: acc, newNames)
            case TestOrStatement.Statement(tree) =>
              loop(tail, tree :: acc, names)
          }
        case Nil =>
          q"""
..${acc.reverse}
suite($name)(
  ..${names.reverse.map(name => Ident(TermName(name))).reduce[Tree]((a, b) => q"$a + $b")}
)"""
      }

    val finalSuite = loop(result, Nil, Nil)

    c.untypecheck(finalSuite)
  }
}
