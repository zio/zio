package zio

import zio.test._
import zio.test.Assertion._
class V0(
  v13: V13,
  v14: V14,
  v10: V10,
  v7: V7,
  v23: V23,
  v27: V27
) {}
object V0 {
  val layer = ZLayer.derive[V0]
}
class V1(
  v3: V3,
  v9: V9
) {}
object V1 {
  val layer = ZLayer.derive[V1]
}
class V2(
  v1: V1,
  v11: V11,
  v22: V22
) {}
object V2 {
  val layer = ZLayer.derive[V2]
}
class V3(
  v21: V21,
  v20: V20,
  v27: V27,
  v19: V19
) {}
object V3 {
  val layer = ZLayer.derive[V3]
}
class V4(
  v25: V25,
  v22: V22
) {}
object V4 {
  val layer = ZLayer.derive[V4]
}
class V5(
  v6: V6,
  v19: V19,
  v3: V3,
  v14: V14,
  v17: V17
) {}
object V5 {
  val layer = ZLayer.derive[V5]
}
class V6(
  v3: V3,
  v23: V23
) {}
object V6 {
  val layer = ZLayer.derive[V6]
}
class V7(
  v13: V13,
  v25: V25
) {}
object V7 {
  val layer = ZLayer.derive[V7]
}
class V8(
  v6: V6,
  v7: V7,
  v5: V5
) {}
object V8 {
  val layer = ZLayer.derive[V8]
}
class V9(
  v15: V15,
  v22: V22,
  v11: V11,
  v21: V21
) {}
object V9 {
  val layer = ZLayer.derive[V9]
}
class V10(
  v2: V2,
  v17: V17,
  v1: V1,
  v21: V21,
  v12: V12,
) {}
object V10 {
  val layer = ZLayer.derive[V10]
}
class V11(
  v15: V15,
  v13: V13
) {}
object V11 {
  val layer = ZLayer.derive[V11]
}
class V12(
  v23: V23,
  v1: V1
) {}
object V12 {
  val layer = ZLayer.derive[V12]
}
class V13(
  v15: V15,
  v4: V4,
  v27: V27
) {}
object V13 {
  val layer = ZLayer.derive[V13]
}
class V14(
  v9: V9,
  v7: V7,
  v17: V17,
  v27: V27,
  v4: V4,
  v11: V11
) {}
object V14 {
  val layer = ZLayer.derive[V14]
}
class V15(
  v22: V22,
  v19: V19
) {}
object V15 {
  val layer = ZLayer.derive[V15]
}
class V16(
  v20: V20,
  v10: V10,
  v24: V24
) {}
object V16 {
  val layer = ZLayer.derive[V16]
}
class V17(
  v23: V23,
  v24: V24,
  v12: V12,
  v15: V15,
  v20: V20,
  v11: V11,
  v22: V22
) {}
object V17 {
  val layer = ZLayer.derive[V17]
}
class V18(
  v0: V0,
  v10: V10,
  v7: V7
) {}
object V18 {
  val layer = ZLayer.derive[V18]
}
class V19(
) {}
object V19 {
  val layer = ZLayer.derive[V19]
}
class V20(
  v4: V4,
  v25: V25,
  v22: V22
) {}
object V20 {
  val layer = ZLayer.derive[V20]
}
class V21(
  v4: V4,
  v11: V11
) {}
object V21 {
  val layer = ZLayer.derive[V21]
}
class V22(
  v19: V19
) {}
object V22 {
  val layer = ZLayer.derive[V22]
}
class V23(
  v13: V13,
  v11: V11,
  v27: V27
) {}
object V23 {
  val layer = ZLayer.derive[V23]
}
class V24(
  v2: V2,
  v15: V15,
  v23: V23
) {}
object V24 {
  val layer = ZLayer.derive[V24]
}
class V25(
  v27: V27
) {}
object V25 {
  val layer = ZLayer.derive[V25]
}
class V26(
  v21: V21,
  v23: V23
) {}
object V26 {
  val layer = ZLayer.derive[V26]
}
class V27(
) {}
object V27 {
  val layer = ZLayer.derive[V27]
}
class V28(
  v16: V16,
  v5: V5
) {}
object V28 {
  val layer = ZLayer.derive[V28]
}
class V29(
  v21: V21,
  v7: V7,
  v26: V26,
  v27: V27
) {}
object V29 {
  val layer = ZLayer.derive[V29]
}


object ManyLayersSpec extends ZIOBaseSpec {

  val t = ZLayer.make[
    V0 &
      V1 &
      V2 &
      V3 &
      V4 &
      V5 &
      V6 &
      V7 &
      V8 &
      V9 &
      V10 &
      V11 &
      V12 &
      V13 &
      V14 &
      V15 &
      V16 &
      V17 &
      V18 &
      V19 &
      V20 &
      V21 &
      V22 &
      V23 &
      V24 &
      V25 &
      V26 &
      V27 &
      V28 &
      Any
  ](
    V0.layer,
    V1.layer,
    V2.layer,
    V3.layer,
    V4.layer,
    V5.layer,
    V6.layer,
    V7.layer,
    V8.layer,
    V9.layer,
    V10.layer,
    V11.layer,
    V12.layer,
    V13.layer,
    V14.layer,
    V15.layer,
    V16.layer,
    V17.layer,
    V18.layer,
    V19.layer,
    V20.layer,
    V21.layer,
    V22.layer,
    V23.layer,
    V24.layer,
    V25.layer,
    V26.layer,
    V27.layer,
    V28.layer,
    )


  def spec = suite("ManyLayersSpec")(
    test("ZLayer.make with many layers compiles") {
      //we just need the file to compile
      assertCompletes
    }
  )
}
