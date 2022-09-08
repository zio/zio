package zio.test.magnolia

import zio.test._
import zio.test.diff.Diff
import DeriveDiff.gen
import java.time.Instant

object DeriveDiffSpec extends ZIOSpecDefault {
  final case class Pet(name: String, hasBone: Boolean, favoriteFoods: List[String], birthday: Instant)
  final case class Person(name: String, nickname: Option[String], age: Int, pet: Pet, person: Option[Person] = None)

  sealed trait Color

  object Color {
    final case class Red(brightness: Int, age: Int = 10)                    extends Color
    final case class Blue(depth: Double, cool: String = "Nice to meet you") extends Color
    final case class Green(isCool: Boolean)                                 extends Color
  }

  def spec: Spec[Any, TestFailure[Any]] = suite("DeriveDiffSpec")(
    suite("derivation")(
      test("case classes can be derived") {
        val l1 = List("Alpha", "This is a wonderful \"way\" to dance and party", "Potato")
        val l2 = List("Alpha", "This is a wonderful way to live and die", "Potato", "Brucee Lee", "Potato", "Ziverge")
        val p1 =
          Person(
            "Boboo",
            Some("Babbo\nThe\nBibber"),
            300,
            Pet("The Beautiful Crumb", false, l1, Instant.MIN),
            Some(
              Person(
                "Boboo",
                Some("Babbo\nThe\nBibber"),
                300,
                Pet("The Beautiful Crumb", false, l1, Instant.MIN)
              )
            )
          )
        val p2 = Person(
          "Bibi",
          Some("Bibbo\nThe\nBibber\nBobber"),
          300,
          Pet("The Beautiful Destroyer", false, l2, Instant.now),
          Some(
            Person(
              "Bibi",
              Some("Bibbo\nThe\nBibber\nBobber"),
              300,
              Pet("The Beautiful Destroyer", false, l2, Instant.now)
            )
          )
        )

        assertTrue(p1 == p2)
      } @@ TestAspect.failing,
      test("sealed traits can be derived") {
        val red: Color  = Color.Blue(10)
        val blue: Color = Color.Blue(30)
        assertTrue(red == blue)
      } @@ TestAspect.failing,
      test("multiline string diffs") {
        val string1 =
          """Good day
How
Are
You
            """.trim
        val string2 =
          """Good morning
Everyone
How
Are
You
Really
Doing
            """.trim
        assertTrue(string1 == string2)
      } @@ TestAspect.failing,
      test("fuzzy equality using diffs") {
        val tree1 = Branch(Leaf(1.0), Leaf(2.0))
        val tree2 = Branch(Leaf(1.01), Leaf(2.0))
        val tree3 = Branch(Leaf(1.02), Leaf(2.0))

        implicit lazy val fuzzyDoubleDiff: Diff[Double] =
          Diff.approximate(0.015)

        assertTrue(
          tree1 == tree2,
          tree1 != tree3
        )
      }
    )
  )

  sealed trait Tree[+A]

  final case class Branch[+A](left: Tree[A], right: Tree[A]) extends Tree[A]
  final case class Leaf[+A](value: A)                        extends Tree[A]

}
