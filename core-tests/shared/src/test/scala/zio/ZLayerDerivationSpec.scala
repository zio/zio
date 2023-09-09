package zio

import com.github.ghik.silencer.silent
import zio.test.Assertion._
import zio.test._

@silent("never used")
object ZLayerDerivationSpec extends ZIOBaseSpec {

  override def spec = suite("ZLayer.derive[A]")(
    basicSuite,
    defaultSuite,
    lifecycleSuite,
    failureSuite
  )

  case class ZeroDep()
  case class OneDep(val d1: String)
  case class TwoDeps(val d1: String, val d2: Int)

  val derivedZero = ZLayer.derive[ZeroDep]
  val derivedOne  = ZLayer.derive[OneDep]
  val derivedTwo  = ZLayer.derive[TwoDeps]

  class Curried(val d1: String)(val d2: OneDep)(implicit val d3: Int)
  val derivedCurried = ZLayer.derive[Curried]

  def basicSuite = suite("derives")(
    test("zero dependency") {
      for {
        d0 <- ZIO.service[ZeroDep]
      } yield assertTrue(d0 == ZeroDep())
    },
    test("one dependency") {
      for {
        d1 <- ZIO.service[OneDep]
      } yield assertTrue(d1 == OneDep("one"))
    },
    test("two dependencies") {
      for {
        d2 <- ZIO.service[TwoDeps]
      } yield assertTrue(d2 == TwoDeps("one", 2))
    },
    test("curried constructor")(
      for {
        c <- ZIO.service[Curried]
      } yield assertTrue(
        c.d1 == "one",
        c.d2 == OneDep("one"),
        c.d3 == 2
      )
    )
  ).provide(
    derivedZero,
    derivedOne,
    derivedTwo,
    derivedCurried,
    ZLayer.succeed("one"),
    ZLayer.succeed(2)
  )

  class ZeroDepAndPromise(val p1: Promise[Nothing, Int])
  class OneDepAndPromise(val d1: String, val p1: Promise[Throwable, Int])

  val derivedZeroAndPromise: ZLayer[Any, Nothing, ZeroDepAndPromise]  = ZLayer.derive[ZeroDepAndPromise]
  val derivedOneAndPromise: ZLayer[String, Nothing, OneDepAndPromise] = ZLayer.derive[OneDepAndPromise]

  class OneDepAndConfig(d1: Int, config: OneDepAndConfig.AConfig)
  object OneDepAndConfig {
    case class AConfig(startTimes: Chunk[java.time.OffsetDateTime])
    implicit val implicitAConfig: Config[AConfig] = Config.fail("failed to load config")
  }
  val derivedOneAndConfig = ZLayer.derive[OneDepAndConfig]

  class OneDepAndDefaultTransitive(i: Int, val d: OneDepAndDefaultTransitive.TransitiveString)
  object OneDepAndDefaultTransitive {
    case class TransitiveString(s: String)
    implicit val defaultTransitiveString: ZLayer.Default.Resolved[String, Nothing, TransitiveString] =
      ZLayer.Default.fromZIO(ZIO.serviceWith[String](TransitiveString(_)))
  }

  val derivedOneAndDefaultTransitive =
    ZLayer.derive[OneDepAndDefaultTransitive]

  val derivedZeroDepAndPromiseOverriden: URLayer[Promise[Nothing, Int], ZeroDepAndPromise] = locally {
    implicit val overridenPromise: ZLayer.Default.Resolved[Promise[Nothing, Int], Nothing, Promise[Nothing, Int]] =
      ZLayer.Default.service[Promise[Nothing, Int]]

    ZLayer.derive[ZeroDepAndPromise]
  }

  def defaultSuite = suite("with ZLayer.Default[A]")(
    test("zero dependency and Promise") {
      for {
        svc    <- ZIO.service[ZeroDepAndPromise]
        isDone <- svc.p1.isDone
      } yield assertTrue(!isDone)
    },
    test("one dependency and Promise") {
      for {
        svc    <- ZIO.service[OneDepAndPromise]
        isDone <- svc.p1.isDone
      } yield assertTrue(svc.d1 == "one", !isDone)
    },
    test("one dependency and implicit zio.Config") {
      for {
        exit <- ZIO.scoped(derivedOneAndConfig.build).exit
      } yield assert(exit)(failsWithA[Config.Error])
    },
    test("one dependency and default value from transitive dependency") {
      for {
        svc <- ZIO.service[OneDepAndDefaultTransitive]
      } yield assertTrue(svc.d.s == "one")
    },
    test("overriden dependency by higher implicit priority") {
      for {
        svc   <- ZIO.service[ZeroDepAndPromise]
        value <- svc.p1.await
      } yield assertTrue(value == 42)
    }.provide(
      derivedZeroDepAndPromiseOverriden,
      ZLayer(Promise.make[Nothing, Int].flatMap(p => p.succeed(42).as(p)))
    )
  ).provide(
    derivedZeroAndPromise,
    derivedOneAndPromise,
    derivedOneAndDefaultTransitive,
    ZLayer.succeed("one"),
    ZLayer.succeed(2)
  )

  class HasLifecycleHooks(ref: Ref[String], shouldFail: Boolean) extends ZLayer.LifecycleHooks[Any, String] {
    override def initialize: ZIO[Any, String, Any] =
      ZIO.fail("Failed!").when(shouldFail) *> ref.set("Initialized")

    override def cleanup: ZIO[Any, Nothing, Any] =
      ref.set("Cleaned up")
  }
  val derivedHasLifecycleHooks: ZLayer[Ref[String] with Boolean, String, HasLifecycleHooks] =
    ZLayer.derive[HasLifecycleHooks]

  def lifecycleSuite = suite("with ZLayer.LifecycleHooks[R, E]")(
    test("initialize and cleanup") {
      for {
        ref       <- Ref.make("")
        shouldFail = false

        afterInit <- ZIO
                       .scoped(for {
                         _   <- derivedHasLifecycleHooks.build
                         str <- ref.get
                       } yield str)
                       .provide(
                         ZLayer.succeed(ref),
                         ZLayer.succeed(shouldFail)
                       )
        afterCleanup <- ref.get
      } yield assertTrue(
        afterInit == "Initialized",
        afterCleanup == "Cleaned up"
      )
    },
    test("can fail during initialize") {
      for {
        ref       <- Ref.make("")
        shouldFail = true

        afterInit <- ZIO
                       .scoped(for {
                         _   <- derivedHasLifecycleHooks.build
                         str <- ref.get
                       } yield str)
                       .exit
                       .provide(
                         ZLayer.succeed(ref),
                         ZLayer.succeed(shouldFail)
                       )
        afterCleanup <- ref.get
      } yield assertTrue(
        afterInit == Exit.fail("Failed!"),
        afterCleanup == ""
      )
    }
  )

  class HasUnresolvedDefault(dep: HasUnresolvedDefault.Dep)
  object HasUnresolvedDefault {
    case class Dep(s: String)

    // correct annotation  : ZLayer.Default.Resolved[Any, Nothing, Dep]
    implicit val defaultDep: ZLayer.Default[Dep] = ZLayer.Default.succeed(Dep("default"))
  }

  abstract class AnAbstractClass(d1: Int)
  trait ATrait

  def failureSuite = suite("fails to derive")(
    test("ZLayer.Default[A] with incorrect type annotation")(
      for {
        res <- typeCheck("ZLayer.derive[HasUnresolvedDefault]")
      } yield
        if (TestVersion.isScala2)
          assertTrue(res.left.exists(_.startsWith("Failed to derive a ZLayer")))
        else
          assertTrue(res.isLeft)
    ),
    test("trait")(
      for {
        res <- typeCheck("ZLayer.derive[ATrait]")
      } yield
        if (TestVersion.isScala2)
          assertTrue(res.left.exists(_.startsWith("Failed to derive a ZLayer")))
        else
          assertTrue(res.isLeft)
    ),
    test("abstract class")(
      for {
        res <- typeCheck("ZLayer.derive[AnAbstractClass]")
      } yield
        if (TestVersion.isScala2)
          assertTrue(res.left.exists(_.startsWith("Failed to derive a ZLayer")))
        else
          assertTrue(res.isLeft)
    ),
    test("java class")(
      for {
        res <- typeCheck("ZLayer.derive[java.util.ArrayList[Int]]")
      } yield
        if (TestVersion.isScala2)
          assertTrue(res.left.exists(_.startsWith("Failed to derive a ZLayer")))
        else
          assertTrue(res.isLeft)
    )
  )
}
