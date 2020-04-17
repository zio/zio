package zio.interop

import scala.scalajs.js.{ Promise => JSPromise }

import zio._
import zio.test.Assertion._
import zio.test._

object JSSpec extends ZIOBaseSpec {
  def spec = suite("JSSpec")(
    suite("Task.fromPromiseJS must")(
      testM("be lazy on the Promise parameter") {
        var evaluated          = false
        def p: JSPromise[Unit] = { evaluated = true; JSPromise.resolve[Unit](()) }
        assertM(Task.fromPromiseJS(p).when(false).as(evaluated))(isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                              = new Exception("no promise for you!")
        lazy val noPromise: JSPromise[Unit] = throw ex
        assertM(Task.fromPromiseJS(noPromise).run)(dies(equalTo(ex)))
      },
      testM("return a Task that fails if Promise is rejected") {
        val ex                            = new Exception("no value for you!")
        lazy val noValue: JSPromise[Unit] = JSPromise.reject(ex)
        assertM(Task.fromPromiseJS(noValue).run)(fails(equalTo(ex)))
      },
      testM("return a Task that produces the value from the Promise") {
        lazy val someValue: JSPromise[Int] = JSPromise.resolve[Int](42)
        assertM(Task.fromPromiseJS(someValue))(equalTo(42))
      },
      testM("handle null produced by the completed Promise") {
        lazy val someValue: JSPromise[String] = JSPromise.resolve[String](null)
        assertM(Task.fromPromiseJS(someValue).map(Option(_)))(isNone)
      }
    ),
    suite("ZIO.fromPromiseJS must")(
      testM("be lazy on the Promise parameter") {
        var evaluated          = false
        def p: JSPromise[Unit] = { evaluated = true; JSPromise.resolve[Unit](()) }
        assertM(ZIO.fromPromiseJS(p).when(false).as(evaluated))(isFalse)
      },
      testM("catch exceptions thrown by lazy block") {
        val ex                              = new Exception("no promise for you!")
        lazy val noPromise: JSPromise[Unit] = throw ex
        assertM(ZIO.fromPromiseJS(noPromise).run)(dies(equalTo(ex)))
      },
      testM("return a ZIO that fails if Promise is rejected") {
        val ex                            = new Exception("no value for you!")
        lazy val noValue: JSPromise[Unit] = JSPromise.reject(ex)
        assertM(ZIO.fromPromiseJS(noValue).run)(fails(equalTo(ex)))
      },
      testM("return a ZIO that produces the value from the Promise") {
        lazy val someValue: JSPromise[Int] = JSPromise.resolve[Int](42)
        assertM(ZIO.fromPromiseJS(someValue))(equalTo(42))
      },
      testM("handle null produced by the completed Promise") {
        lazy val someValue: JSPromise[String] = JSPromise.resolve[String](null)
        assertM(ZIO.fromPromiseJS(someValue).map(Option(_)))(isNone)
      }
    ),
    suite("ZIO#toPromiseJS must")(
      testM("produce a rejected Promise from a failed ZIO") {
        val ex                          = new Exception("Ouch")
        val failedZIO: Task[Unit]       = Task.fail(ex)
        val rejectedPromise: Task[Unit] = failedZIO.toPromiseJS.flatMap(p => ZIO.fromFuture(_ => p.toFuture))
        assertM(rejectedPromise.run)(fails(equalTo(ex)))
      },
      testM("produce a resolved Promise from a successful ZIO") {
        val zio                        = Task.succeed(42)
        val resolvedPromise: Task[Int] = zio.toPromiseJS.flatMap(p => ZIO.fromFuture(_ => p.toFuture))
        assertM(resolvedPromise)(equalTo(42))
      }
    )
  )
}
