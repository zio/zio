package zio.test.laws

object ZLawfulF2 {

  trait Divariant[-CapsBoth[_[-_, +_]], -CapsLeft[_], -CapsRight[_], -R] { self =>
    def laws: ZLawsF2.Divariant[CapsBoth, CapsLeft, CapsRight, R]

    def +[CapsBoth1[x[-_, +_]] <: CapsBoth[x], CapsLeft1[x] <: CapsLeft[x], CapsRight1[x] <: CapsRight[x], R1 <: R](
      that: Divariant[CapsBoth1, CapsLeft1, CapsRight1, R1]
    ): Divariant[CapsBoth1, CapsLeft1, CapsRight1, R1] =
      new Divariant[CapsBoth1, CapsLeft1, CapsRight1, R1] {
        val laws = self.laws + that.laws
      }
  }
}
