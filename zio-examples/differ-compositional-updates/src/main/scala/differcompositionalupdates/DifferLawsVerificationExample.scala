package differcompositionalupdates

import zio._

/** Title: The Four Differ Laws — Verified by Hand
  * Description: Manually verifies all four correctness laws for a custom addDiffer:
  * associativity, identity, self-diff-is-empty, and round-trip. Violations would
  * silently corrupt FiberRef state on join.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.DifferLawsVerificationExample"
  */
object DifferLawsVerificationExample extends ZIOAppDefault {

  val addDiffer: Differ[Int, Int] = new Differ[Int, Int] {
    def combine(first: Int, second: Int): Int   = first + second
    def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
    def empty: Int                              = 0
    def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  }

  def checkLaw(name: String, holds: Boolean): ZIO[Any, Nothing, Unit] =
    Console.printLine(s"  [${ if (holds) "PASS" else "FAIL" }] $name").orDie

  override def run: ZIO[Any, Any, Unit] = {
    val p1 = addDiffer.diff(0, 3)    // delta = 3
    val p2 = addDiffer.diff(3, 7)    // delta = 4
    val p3 = addDiffer.diff(7, 12)   // delta = 5

    for {
      _ <- Console.printLine("=== Concept 4: The Four Differ Laws ===").orDie

      // Law 1: combine is associative
      // (p1 combine p2) combine p3  ==  p1 combine (p2 combine p3)
      _ <- {
        val lhs = addDiffer.patch(addDiffer.combine(addDiffer.combine(p1, p2), p3))(0)
        val rhs = addDiffer.patch(addDiffer.combine(p1, addDiffer.combine(p2, p3)))(0)
        checkLaw("combine is associative", lhs == rhs)
      }

      // Law 2: empty is the identity for combine
      // patch(combine(p, empty))(old) == patch(p)(old)
      _ <- {
        val p   = addDiffer.diff(0, 5)
        val lhs = addDiffer.patch(addDiffer.combine(p, addDiffer.empty))(0)
        val rhs = addDiffer.patch(addDiffer.combine(addDiffer.empty, p))(0)
        checkLaw("empty is the combine identity", lhs == 5 && rhs == 5)
      }

      // Law 3: diffing a value with itself returns empty
      _ <- {
        val selfPatch = addDiffer.diff(42, 42)
        checkLaw("diff(v, v) == empty", selfPatch == addDiffer.empty)
      }

      // Law 4: diff then patch is a round-trip
      // patch(diff(old, new))(old) == new
      _ <- {
        val p      = addDiffer.diff(0, 15)
        val result = addDiffer.patch(p)(0)
        checkLaw("patch(diff(old, new))(old) == new", result == 15)
      }

      // Law 5: patching with empty is identity
      // patch(empty)(v) == v
      _ <- {
        val result = addDiffer.patch(addDiffer.empty)(42)
        checkLaw("patch(empty)(v) == v", result == 42)
      }
    } yield ()
  }
}
