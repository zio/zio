//package zio.examples
//
//import zio._
//
//object ZIOAppExample extends ZIOAppDefault {
//  trait Animal
//  trait Dog extends Animal
//  type Cool <: Dog
//
//  type C = Clock
//
//  def run =
//    (ZIO(Tag[Animal]) <*>
//      ZIO(Tag[C])).tap(t => ZIO.debug(t._1.render + " == " + t._2.render)).map(_ == _).debug("EQ")
//}

import zio._
import zio.test.TestEnvironment

object Fun {

  final case class Box[A, B]()

  // TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix, "<root>", scala.collection.immutable.List()), "<empty>", scala.collection.immutable.List()), "Fun", scala.collection.immutable.List()), "Box", scala.collection.immutable.List(TypeRef(TypeRef(TypeRef(TypeRef(NoPrefix, "<root>", scala.collection.immutable.List()), "java", scala.collection.immutable.List()), "lang", scala.collection.immutable.List()), "String", scala.collection.immutable.List()), TypeRef(TypeRef(TypeRef(NoPrefix, "<root>", scala.collection.immutable.List()), "scala", scala.collection.immutable.List()), "Boolean", scala.collection.immutable.List())))
  /**
   * The main function of the application, which will be passed the command-line
   * arguments to the program and has to return an `IO` with the errors fully
   * handled.
   */
  def main(args: Array[String]): Unit = {
    println(Tag[Has[Int] with Has[zio.test.Live]])
    println(Tag[Has[Int] with Has[zio.test.Live]].render)
    println(Tag[TestEnvironment].render)
  }

}
