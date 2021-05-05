package zio.macros

import zio._
import zio.test.Assertion._
import zio.test._

object LayerSpec extends DefaultRunnableSpec {
  def spec: ZSpec[Environment, Failure] = suite("LayerSpec")(
    suite("Layer macro")(
      testM("generates a companion object with a layer method") {
        assertM(typeCheck {
          """
            trait Bar
            trait Baz
            trait Qux
            
            @layer[Bar]
            class Foo(int: Int, string: String) extends Bar with Baz with Qux
            
            @layer[Baz]
            class X extends Baz
            
            object Test {
              val bar: ZLayer[Has[Int] with Has[String], Nothing, Has[Bar]] = Foo.layer
              val baz: ZLayer[Any, Nothing, Has[Baz]] = X.layer
            }
          """
        })(isRight(anything))
      },
      testM("generates a layer method and preserves the contents of existing companion") {
        assertM(typeCheck {
          """
            trait Bar
            trait Baz
            trait Qux
            
            @layer[Bar]
            class Foo(int: Int, string: String) extends Bar with Baz with Qux
            
            object Foo {
               def unit: Unit = ()
            }
            
            object Test {
              val layer: ZLayer[Has[Int] with Has[String], Nothing, Has[Bar]] = Foo.layer
              val unit: Unit = Foo.unit
            }
          """
        })(isRight(anything))
      }
    )
  )
}
