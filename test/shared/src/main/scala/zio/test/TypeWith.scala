/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

package zio.test

/**
 * `TypeWith[Ev[_]]` provides evidence that an instance of `Ev[Type]` exists
 * for some concrete but unknown type. This allows construction of polymorphic
 * generators where the the type is known to satisfy certain constraints even
 * thought the type itself is unknown.
 *
 * For instance, consider the following generalized algebraic data type:
 *
 * {{{
 * sealed trait Expr[+A] extends Product with Serializable
 *
 * final case class Value[+A](value: A) extends Expr[A]
 * final case class Mapping[A, +B](expr: Expr[A], f: A => B) extends Expr[B]
 * }}}
 *
 * We would like to test that for any expression we can fuse two mappings. We
 * want to create instances of `Expr` that reflect the full range of values
 * that an `Expr` can take, including multiple layers of nesting mappings and
 * mappings between different types.
 *
 * To do this, we first define a type alias for the constraint that all
 * generated values must satisfy:
 *
 * {{{
 * type Testing[+A] = Gen[Random with Sized, A]
 * }}}
 *
 * In other words, we must have a `Gen` for any value we want to generate.
 *
 * Next, we use the `TypeWith` constructor to existentially hide the types of
 * the values we want to use:
 *
 * {{{
 * val genInt: TypeWith[Testing]    = TypeWith(Gen.anyInt)
 * val genString: TypeWith[Testing] = TypeWith(Gen.anyString)
 *
 * val genPoly: Gen[Random, TypeWith[Testing]] = Gen.elements(genInt, genString)
 * }}}
 *
 * Then we can define polymorphic generators for expressions:
 *
 * {{{
 * def genValue(t: TypeWith[Testing]): Gen[Random with Sized, Expr[t.Type]] =
 *   t.evidence.map(Value(_))
 *
 *  def genMapping(t: TypeWith[Testing]): Gen[Random with Sized, Expr[t.Type]] =
 *    Gen.suspend {
 *      genPoly.flatMap { t0 =>
 *        genExpr(t0).flatMap { expr =>
 *          val genFunction: Testing[t0.Type => t.Type] = Gen.function(t.evidence)
 *          val genExpr1: Testing[Expr[t.Type]]         = genFunction.map(f => Mapping(expr, f))
 *          genExpr1
 *        }
 *      }
 *    }
 *
 *  def genExpr(t: TypeWith[Testing]): Gen[Random with Sized, Expr[t.Type]] =
 *    Gen.oneOf(genMapping(t), genValue(t))
 * }}}
 *
 * Finally, we can test our property:
 *
 * {{{
 * testM("map fusion") {
 *   check(genPoly.flatMap(genExpr(_))) { expr =>
 *     assert(eval(fuse(expr)))(equalTo(eval(expr)))
 *   }
 * }
 * }}}
 *
 * This will generate expressions with multiple levels of nesting and
 * polymorphic mappings between different types, making sure that the types
 * line up for each mapping. This provides a higher level of confidence in
 * properties than testing with a monomorphic value.
 *
 * The design is heavily inspired by Erik Osheim's design
 * [[http://plastic-idolatry.com/erik/oslo2019.pdf]]
 */
trait TypeWith[Ev[_]] {
  type Type
  def evidence: Ev[Type]
}

object TypeWith {

  /**
   * Constructs an instance of `TypeWith` using the specified value,
   * existentially hiding the underlying type.
   */
  def apply[Ev[_], A](ev: Ev[A]): TypeWith[Ev] =
    new TypeWith[Ev] {
      type Type = A
      val evidence = ev
    }
}
