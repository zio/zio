package examples


//import zio.ZIO
//import zio.test.Spec.TestCase
//import zio.test._
//import org.specs2.concurrent.ExecutionEnv

//object PredicatesExampleSpec extends DefaultRunnableSpec {
//
//  test("1 + 1 must be 2")  {
//
//    assert[Int]( 1 + 1, Predicate.equals(2))
//
//  }
//
//}

//
//import zio.test._
//import zio.clock.nanoTime
//import Predicate.gt
//
//object MyTest extends DefaultRunnableSpec {
//      suite("clock") {
//          testM("time is non-zero") {
//              assertM(nanoTime, gt(0))
//            }
//        }
//    }