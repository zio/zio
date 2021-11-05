package zio

import zio.test._

import scala.annotation.tailrec

// TODO Replace this with ZIOBase2Spec once bugs are fixed
trait ZIOBaseOldSpec extends ZIOSpecDefault {
  override def aspects: Chunk[TestAspectAtLeastR[Has[Live]]] =
    if (TestPlatform.isJVM) Chunk(TestAspect.timeout(120.seconds))
    else Chunk(TestAspect.sequential, TestAspect.timeout(120.seconds))

//  override def runner: TestRunner[Environment, Any] =
//    defaultTestRunner.withRuntimeConfig(self =>
//      self.copy(runtimeConfigFlags = self.runtimeConfigFlags + RuntimeConfigFlag.EnableCurrentFiber)
//    )

  sealed trait ZIOTag {
    val value: String
    val subTags: List[ZIOTag] = Nil
  }
  object ZIOTag {
    case object errors extends ZIOTag { override val value = "errors" }
    case object future extends ZIOTag { override val value = "future" }
    case object interop extends ZIOTag {
      override val value                 = "interop"
      override val subTags: List[ZIOTag] = List(future)
    }
    case object interruption extends ZIOTag { override val value = "interruption" }
    case object regression   extends ZIOTag { override val value = "regression"   }
    case object supervision  extends ZIOTag { override val value = "supervision"  }
  }

  def zioTag(zioTag: ZIOTag, zioTags: ZIOTag*): TestAspectPoly = {
    val tags = zioTags.map(_.value) ++ getSubTags(zioTag) ++ zioTags.flatMap(getSubTags)
    TestAspect.tag(zioTag.value, tags.distinct: _*)
  }

  private def getSubTags(zioTag: ZIOTag): List[String] = {
    @tailrec
    def loop(currentZioTag: ZIOTag, remainingZioTags: List[ZIOTag], result: List[String]): List[String] =
      (currentZioTag.subTags, remainingZioTags) match {
        case (Nil, Nil)      => currentZioTag.value :: result
        case (Nil, t :: ts)  => loop(t, ts, currentZioTag.value :: result)
        case (st :: sts, ts) => loop(st, sts ++ ts, currentZioTag.value :: result)
      }
    zioTag.subTags match {
      case t :: ts => loop(t, ts, Nil)
      case Nil     => Nil
    }
  }

  /**
   * Builds a suite containing a number of other specs.
   */
  override def suite[In](label: String)(specs: In*)(implicit
    suiteConstructor: SuiteConstructor[In],
    trace: ZTraceElement
  ): Spec[suiteConstructor.OutEnvironment, suiteConstructor.OutError, suiteConstructor.OutSuccess] =
    zio.test.suite(label)(specs: _*)

  /**
   * Builds an effectual suite containing a number of other specs.
   */
  @deprecated("use suite", "2.0.0")
  def suiteM[R, E, T](label: String)(specs: ZIO[R, E, Iterable[Spec[R, E, T]]])(implicit
    trace: ZTraceElement
  ): Spec[R, E, T] =
    suite(label)(specs)

  /**
   * Builds a spec with a single test.
   */
  override def test[In](label: String)(
    assertion: => In
  )(implicit
    testConstructor: TestConstructor[Nothing, In],
    trace: ZTraceElement
  ): testConstructor.Out =
    zio.test.test(label)(assertion)

  /**
   * Builds a spec with a single effectful test.
   */
  @deprecated("use test", "2.0.0")
  def testM[R, E](label: String)(
    assertion: => ZIO[R, E, TestResult]
  )(implicit trace: ZTraceElement): ZSpec[R, E] =
    test(label)(assertion)
}
