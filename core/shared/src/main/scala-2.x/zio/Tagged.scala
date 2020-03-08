/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import com.github.ghik.silencer.silent

final case class Tagged[A](tag: TaggedType[A]) {
  override def equals(that: Any): Boolean = that match {
    case Tagged(that) => tag.tag == that.tag
    case _            => false
  }
  override def hashCode: Int    = tag.tag.hashCode
  override def toString: String = tag.tag.toString
}

object Tagged {

  implicit def tagged[A: TaggedType](implicit a: TaggedType[A]): Tagged[A] =
    Tagged(a)

  @silent("is never used")
  implicit def taggedF[F[_], A0](implicit tag: TaggedTypeF[F], a0: Tagged[A0]): Tagged[F[A0]] = {
    implicit val tag0 = a0.tag
    Tagged(TaggedType[F[A0]])
  }

  @silent("is never used")
  implicit def taggedF2[F[_, _], A0, A1](
    implicit tag: TaggedTypeF2[F],
    a0: Tagged[A0],
    a1: Tagged[A1]
  ): Tagged[F[A0, A1]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    Tagged(TaggedType[F[A0, A1]])
  }

  @silent("is never used")
  implicit def taggedF3[F[_, _, _], A0, A1, A2](
    implicit tag: TaggedTypeF3[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2]
  ): Tagged[F[A0, A1, A2]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    implicit val tag2 = a2.tag
    Tagged(TaggedType[F[A0, A1, A2]])
  }

  @silent("is never used")
  implicit def taggedF4[F[_, _, _, _], A0, A1, A2, A3](
    implicit tag: TaggedTypeF4[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3]
  ): Tagged[F[A0, A1, A2, A3]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    implicit val tag2 = a2.tag
    implicit val tag3 = a3.tag
    Tagged(TaggedType[F[A0, A1, A2, A3]])
  }

  @silent("is never used")
  implicit def taggedF5[F[_, _, _, _, _], A0, A1, A2, A3, A4](
    implicit tag: TaggedTypeF5[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4]
  ): Tagged[F[A0, A1, A2, A3, A4]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    implicit val tag2 = a2.tag
    implicit val tag3 = a3.tag
    implicit val tag4 = a4.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4]])
  }

  @silent("is never used")
  implicit def taggedF6[F[_, _, _, _, _, _], A0, A1, A2, A3, A4, A5](
    implicit tag: TaggedTypeF6[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5]
  ): Tagged[F[A0, A1, A2, A3, A4, A5]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    implicit val tag2 = a2.tag
    implicit val tag3 = a3.tag
    implicit val tag4 = a4.tag
    implicit val tag5 = a5.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5]])
  }

  @silent("is never used")
  implicit def taggedF7[F[_, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6](
    implicit tag: TaggedTypeF7[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    implicit val tag2 = a2.tag
    implicit val tag3 = a3.tag
    implicit val tag4 = a4.tag
    implicit val tag5 = a5.tag
    implicit val tag6 = a6.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6]])
  }

 @silent("is never used")
  implicit def taggedF8[F[_, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7](
    implicit tag: TaggedTypeF8[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    implicit val tag2 = a2.tag
    implicit val tag3 = a3.tag
    implicit val tag4 = a4.tag
    implicit val tag5 = a5.tag
    implicit val tag6 = a6.tag
    implicit val tag7 = a7.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7]])
  }

 @silent("is never used")
  implicit def taggedF9[F[_, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8](
    implicit tag: TaggedTypeF9[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    implicit val tag2 = a2.tag
    implicit val tag3 = a3.tag
    implicit val tag4 = a4.tag
    implicit val tag5 = a5.tag
    implicit val tag6 = a6.tag
    implicit val tag7 = a7.tag
    implicit val tag8 = a8.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8]])
  }

 @silent("is never used")
  implicit def taggedF10[F[_, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9](
    implicit tag: TaggedTypeF10[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9]] = {
    implicit val tag0 = a0.tag
    implicit val tag1 = a1.tag
    implicit val tag2 = a2.tag
    implicit val tag3 = a3.tag
    implicit val tag4 = a4.tag
    implicit val tag5 = a5.tag
    implicit val tag6 = a6.tag
    implicit val tag7 = a7.tag
    implicit val tag8 = a8.tag
    implicit val tag9 = a9.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9]])
  }

 @silent("is never used")
  implicit def taggedF11[F[_, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10](
    implicit tag: TaggedTypeF11[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10]])
  }

  @silent("is never used")
  implicit def taggedF12[F[_, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11](
    implicit tag: TaggedTypeF12[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11]])
  }

  @silent("is never used")
  implicit def taggedF13[F[_, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12](
    implicit tag: TaggedTypeF13[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12]])
  }

  @silent("is never used")
  implicit def taggedF14[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13](
    implicit tag: TaggedTypeF14[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13]])
  }

  @silent("is never used")
  implicit def taggedF15[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14](
    implicit tag: TaggedTypeF15[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13],
    a14: Tagged[A14]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    implicit val tag14 = a14.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14]])
  }

  @silent("is never used")
  implicit def taggedF16[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15](
    implicit tag: TaggedTypeF16[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13],
    a14: Tagged[A14],
    a15: Tagged[A15]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    implicit val tag14 = a14.tag
    implicit val tag15 = a15.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15]])
  }

  @silent("is never used")
  implicit def taggedF17[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16](
    implicit tag: TaggedTypeF17[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13],
    a14: Tagged[A14],
    a15: Tagged[A15],
    a16: Tagged[A16]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    implicit val tag14 = a14.tag
    implicit val tag15 = a15.tag
    implicit val tag16 = a16.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16]])
  }

  @silent("is never used")
  implicit def taggedF18[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17](
    implicit tag: TaggedTypeF18[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13],
    a14: Tagged[A14],
    a15: Tagged[A15],
    a16: Tagged[A16],
    a17: Tagged[A17]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    implicit val tag14 = a14.tag
    implicit val tag15 = a15.tag
    implicit val tag16 = a16.tag
    implicit val tag17 = a17.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17]])
  }

  @silent("is never used")
  implicit def taggedF19[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18](
    implicit tag: TaggedTypeF19[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13],
    a14: Tagged[A14],
    a15: Tagged[A15],
    a16: Tagged[A16],
    a17: Tagged[A17],
    a18: Tagged[A18]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    implicit val tag14 = a14.tag
    implicit val tag15 = a15.tag
    implicit val tag16 = a16.tag
    implicit val tag17 = a17.tag
    implicit val tag18 = a18.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18]])
  }

  @silent("is never used")
  implicit def taggedF20[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19](
    implicit tag: TaggedTypeF20[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13],
    a14: Tagged[A14],
    a15: Tagged[A15],
    a16: Tagged[A16],
    a17: Tagged[A17],
    a18: Tagged[A18],
    a19: Tagged[A19]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    implicit val tag14 = a14.tag
    implicit val tag15 = a15.tag
    implicit val tag16 = a16.tag
    implicit val tag17 = a17.tag
    implicit val tag18 = a18.tag
    implicit val tag19 = a19.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19]])
  }

  @silent("is never used")
  implicit def taggedF21[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20](
    implicit tag: TaggedTypeF21[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13],
    a14: Tagged[A14],
    a15: Tagged[A15],
    a16: Tagged[A16],
    a17: Tagged[A17],
    a18: Tagged[A18],
    a19: Tagged[A19],
    a20: Tagged[A20]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    implicit val tag14 = a14.tag
    implicit val tag15 = a15.tag
    implicit val tag16 = a16.tag
    implicit val tag17 = a17.tag
    implicit val tag18 = a18.tag
    implicit val tag19 = a19.tag
    implicit val tag20 = a20.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20]])
  }

  @silent("is never used")
  implicit def taggedF22[F[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _], A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21](
    implicit tag: TaggedTypeF22[F],
    a0: Tagged[A0],
    a1: Tagged[A1],
    a2: Tagged[A2],
    a3: Tagged[A3],
    a4: Tagged[A4],
    a5: Tagged[A5],
    a6: Tagged[A6],
    a7: Tagged[A7],
    a8: Tagged[A8],
    a9: Tagged[A9],
    a10: Tagged[A10],
    a11: Tagged[A11],
    a12: Tagged[A12],
    a13: Tagged[A13],
    a14: Tagged[A14],
    a15: Tagged[A15],
    a16: Tagged[A16],
    a17: Tagged[A17],
    a18: Tagged[A18],
    a19: Tagged[A19],
    a20: Tagged[A20],
    a21: Tagged[A21]
  ): Tagged[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21]] = {
    implicit val tag0  = a0.tag
    implicit val tag1  = a1.tag
    implicit val tag2  = a2.tag
    implicit val tag3  = a3.tag
    implicit val tag4  = a4.tag
    implicit val tag5  = a5.tag
    implicit val tag6  = a6.tag
    implicit val tag7  = a7.tag
    implicit val tag8  = a8.tag
    implicit val tag9  = a9.tag
    implicit val tag10 = a10.tag
    implicit val tag11 = a11.tag
    implicit val tag12 = a12.tag
    implicit val tag13 = a13.tag
    implicit val tag14 = a14.tag
    implicit val tag15 = a15.tag
    implicit val tag16 = a16.tag
    implicit val tag17 = a17.tag
    implicit val tag18 = a18.tag
    implicit val tag19 = a19.tag
    implicit val tag20 = a20.tag
    implicit val tag21 = a21.tag
    Tagged(TaggedType[F[A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21]])
  }
}
