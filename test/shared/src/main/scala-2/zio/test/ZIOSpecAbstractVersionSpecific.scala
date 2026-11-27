package zio.test

private[test] trait ZIOSpecAbstractVersionSpecific {

  /**
   * An implicit conversion, checked by a macro, that lets a spec be used where
   * a spec with the environment `R1` is expected. When the spec requires a
   * service that `R1` does not contain, compilation fails with an error that
   * names the missing services; otherwise the spec is returned unchanged.
   */
  implicit def validateEnv[R1, R, E](spec: Spec[R, E]): Spec[R1, E] =
    macro SpecLayerMacros.validate[R1, R]

}
