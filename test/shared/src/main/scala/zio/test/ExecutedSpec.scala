package zio.test

import zio.test.ExecutedSpec._

sealed trait ExecutedSpec[+E] { self =>

  def annotationsMap: TestAnnotationMap =
    self match {
      case Suite(_, specs) =>
        specs.foldLeft(TestAnnotationMap.empty)((annotations, spec) => annotations ++ spec.annotationsMap)
      case Test(_, _, annotations) => annotations
    }

  def countFailures: Int =
    self match {
      case Suite(_, specs)  => specs.foldLeft(0)((n, spec) => n + spec.countFailures)
      case Test(_, test, _) => test.fold(_ => 1, _ => 0)
    }

  def countIgnored: Int =
    self match {
      case Suite(_, specs)  => specs.foldLeft(0)((n, spec) => n + spec.countIgnored)
      case Test(_, test, _) => test.fold(_ => 0, { case TestSuccess.Ignored => 1; case _ => 0 })
    }

  def countSuccesses: Int =
    self match {
      case Suite(_, specs)  => specs.foldLeft(0)((n, spec) => n + spec.countSuccesses)
      case Test(_, test, _) => test.fold(_ => 0, { case TestSuccess.Succeeded(_) => 1; case _ => 0 })
    }

  def failures: Seq[ExecutedSpec[E]] =
    self match {
      case Suite(label, specs) =>
        val failures = specs.flatMap(_.failures)
        if (failures.isEmpty) Seq.empty else Seq(Suite(label, failures))
      case c @ Test(_, test, _) => if (test.isLeft) Seq(c) else Seq.empty
    }

  def flattenTests[Z](f: Test[E] => Z): Seq[Z] =
    self match {
      case Suite(_, specs)   => specs.flatMap(_.flattenTests((f)))
      case c @ Test(_, _, _) => Seq(f(c))
    }

  def forAllTests(f: Either[TestFailure[E], TestSuccess] => Boolean): Boolean =
    self match {
      case Suite(_, specs)  => specs.forall(_.forAllTests(f))
      case Test(_, test, _) => f(test)
    }

  def hasFailures: Boolean =
    self match {
      case Suite(_, specs)  => specs.exists(_.hasFailures)
      case Test(_, test, _) => test.isLeft
    }

  def isEmpty: Boolean =
    self match {
      case Suite(_, specs) => specs.forall(_.isEmpty)
      case Test(_, _, _)   => false
    }
}

object ExecutedSpec {

  final case class Suite[+E](label: String, specs: Vector[ExecutedSpec[E]]) extends ExecutedSpec[E]
  final case class Test[+E](label: String, test: Either[TestFailure[E], TestSuccess], annotations: TestAnnotationMap)
      extends ExecutedSpec[E]

  def suite[E](label: String, specs: Vector[ExecutedSpec[E]]): ExecutedSpec[E] =
    Suite(label, specs)

  def test[E](
    label: String,
    test: Either[TestFailure[E], TestSuccess],
    annotations: TestAnnotationMap
  ): ExecutedSpec[E] =
    Test(label, test, annotations)
}
