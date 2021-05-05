package zio.macros

import zio._
import zio.test.Assertion._
import zio.test._

object LayerGenMacroTest extends DefaultRunnableSpec {
  def spec: ZSpec[Environment, Failure] =
    suite("LayerFromConstructorSpec")(suite("ZLayer.fromConstructor syntax")(testM("generates layer from constructor") {
      assertM(typeCheck {
        """
          |import zio._
          |import zio.macros._
          |
          |trait Bar
          |trait Baz
          |trait Qux
          |
          |final class Foo(int: Int, string: String) extends Bar with Baz with Qux
          |
          |final class X extends Baz
          |
          |object Test {
          |  val bar: zio.ZLayer[Has[Int] with Has[String], Nothing, Has[Bar]] = 
          |   ZLayer.fromConstructor[Foo, Bar]
          |  
          |  val qux: zio.ZLayer[Has[Int] with Has[String], Nothing, Has[Qux]] = 
          |   ZLayer.fromConstructor[Foo, Qux]
          |    
          |  val baz: ZLayer[Any, Nothing, Has[Baz]] = 
          |   ZLayer.fromConstructor[X, Baz]
          |}
          |""".stripMargin
      })
    }))
}
