package zio.test

//import zio._
//import zio.test._
import zio.test.TestAspect._
//import org.junit.runner.RunWith

object AnnotationReproducer extends ZIOSpecDefault {
  override def spec = suite("a")(
    test("a")(assertTrue(true)),
    test("b")(assertTrue(false)),
    test("c")(assertTrue(false)) @@ ignore,
  ) @@ timed
}
