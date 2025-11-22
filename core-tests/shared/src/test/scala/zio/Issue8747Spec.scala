package zio

import zio.test._

/**
 * Automated test suite for Issue #8747
 * 
 * This test verifies that stack traces are properly captured for both
 * left-hand side (LHS) and right-hand side (RHS) flatMap operations.
 * 
 * Issue: When the right-hand effect fails, the printed stacktrace only shows
 * the construction frames of the left-hand side, and omits the right-hand side.
 * 
 * To run this test:
 * ```
 * sbt "coreJVM/testOnly *Issue8747Spec"
 * ```
 * 
 * Or run all core tests:
 * ```
 * sbt "coreJVM/test"
 * ```
 */
object Issue8747Spec extends ZIOSpecDefault {

  def spec = suite("Issue #8747: RHS flatMap trace capture")(
    
    test("Example A (baseline): LHS flatMap captures all stack frames") {
      // This test verifies the CORRECT behavior that should work both before and after the fix
      
      def a(implicit trace: Trace): ZIO[Any, String, Unit] = 
        b.flatMap(_ => ZIO.unit)
      
      def b(implicit trace: Trace): ZIO[Any, String, Unit] = 
        c.flatMap(_ => ZIO.unit)
      
      def c(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.fail("The failure")

      for {
        exit <- a.exit
        result <- exit match {
          case Exit.Failure(cause) =>
            val stackTrace = cause.prettyPrint
            
            // Verify all frames are present
            val hasA = stackTrace.contains("a(Issue8747Spec.scala:")
            val hasB = stackTrace.contains("b(Issue8747Spec.scala:")
            val hasC = stackTrace.contains("c(Issue8747Spec.scala:")
            
            ZIO.succeed((hasA, hasB, hasC, stackTrace))
          
          case Exit.Success(_) =>
            ZIO.fail("Expected failure but got success")
        }
      } yield {
        val (hasA, hasB, hasC, stackTrace) = result
        
        assertTrue(
          hasA,
          hasB,
          hasC
        ) ?? s"LHS flatMap should capture all frames (a, b, c). Stack trace:\n$stackTrace"
      }
    },
    
    test("Example B (bug fix): RHS flatMap captures all stack frames") {
      // This test verifies the FIX - before the fix, this would FAIL
      // After the fix, this should PASS
      
      def a(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => b)
      
      def b(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => c)
      
      def c(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.fail("The failure")

      for {
        exit <- a.exit
        result <- exit match {
          case Exit.Failure(cause) =>
            val stackTrace = cause.prettyPrint
            
            // Verify all frames are present
            val hasA = stackTrace.contains("a(Issue8747Spec.scala:")
            val hasB = stackTrace.contains("b(Issue8747Spec.scala:")
            val hasC = stackTrace.contains("c(Issue8747Spec.scala:")
            
            ZIO.succeed((hasA, hasB, hasC, stackTrace))
          
          case Exit.Success(_) =>
            ZIO.fail("Expected failure but got success")
        }
      } yield {
        val (hasA, hasB, hasC, stackTrace) = result
        
        // CRITICAL TEST: Before fix, only hasC would be true
        // After fix, all three should be true
        assertTrue(
          hasA,
          hasB,
          hasC
        ) ?? s"RHS flatMap should capture all frames (a, b, c). Stack trace:\n$stackTrace"
      }
    },
    
    test("Deep nesting: RHS flatMap captures all intermediate frames") {
      // Test with deeper nesting to ensure the fix works at multiple levels
      
      def level1(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => level2)
      
      def level2(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => level3)
      
      def level3(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => level4)
      
      def level4(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => level5)
      
      def level5(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.fail("Deep failure")

      for {
        exit <- level1.exit
        result <- exit match {
          case Exit.Failure(cause) =>
            val stackTrace = cause.prettyPrint
            
            val has1 = stackTrace.contains("level1(Issue8747Spec.scala:")
            val has2 = stackTrace.contains("level2(Issue8747Spec.scala:")
            val has3 = stackTrace.contains("level3(Issue8747Spec.scala:")
            val has4 = stackTrace.contains("level4(Issue8747Spec.scala:")
            val has5 = stackTrace.contains("level5(Issue8747Spec.scala:")
            
            ZIO.succeed((has1, has2, has3, has4, has5, stackTrace))
          
          case Exit.Success(_) =>
            ZIO.fail("Expected failure but got success")
        }
      } yield {
        val (has1, has2, has3, has4, has5, stackTrace) = result
        
        assertTrue(
          has1,
          has2,
          has3,
          has4,
          has5
        ) ?? s"Deep nesting should capture all frames. Stack trace:\n$stackTrace"
      }
    },
    
    test("Mixed LHS and RHS: both patterns capture frames correctly") {
      // Test mixing LHS and RHS flatMap in the same chain
      
      def step1(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => step2)  // RHS
      
      def step2(implicit trace: Trace): ZIO[Any, String, Unit] = 
        step3.flatMap(_ => ZIO.unit)  // LHS
      
      def step3(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => step4)  // RHS
      
      def step4(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.fail("Mixed failure")

      for {
        exit <- step1.exit
        result <- exit match {
          case Exit.Failure(cause) =>
            val stackTrace = cause.prettyPrint
            
            val has1 = stackTrace.contains("step1(Issue8747Spec.scala:")
            val has2 = stackTrace.contains("step2(Issue8747Spec.scala:")
            val has3 = stackTrace.contains("step3(Issue8747Spec.scala:")
            val has4 = stackTrace.contains("step4(Issue8747Spec.scala:")
            
            ZIO.succeed((has1, has2, has3, has4, stackTrace))
          
          case Exit.Success(_) =>
            ZIO.fail("Expected failure but got success")
        }
      } yield {
        val (has1, has2, has3, has4, stackTrace) = result
        
        assertTrue(
          has1,
          has2,
          has3,
          has4
        ) ?? s"Mixed LHS/RHS should capture all frames. Stack trace:\n$stackTrace"
      }
    },
    
    test("File names and line numbers are present in stack trace") {
      // Verify that stack traces include proper source location information
      
      def outer(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => inner)
      
      def inner(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.fail("Location test")

      for {
        exit <- outer.exit
        result <- exit match {
          case Exit.Failure(cause) =>
            val stackTrace = cause.prettyPrint
            
            // Check for file name
            val hasFileName = stackTrace.contains("Issue8747Spec.scala")
            
            // Check for line numbers (format: "file.scala:123")
            val hasLineNumbers = stackTrace.matches("(?s).*Issue8747Spec\\.scala:\\d+.*")
            
            // Check for function names
            val hasOuter = stackTrace.contains("outer(Issue8747Spec.scala:")
            val hasInner = stackTrace.contains("inner(Issue8747Spec.scala:")
            
            ZIO.succeed((hasFileName, hasLineNumbers, hasOuter, hasInner, stackTrace))
          
          case Exit.Success(_) =>
            ZIO.fail("Expected failure but got success")
        }
      } yield {
        val (hasFileName, hasLineNumbers, hasOuter, hasInner, stackTrace) = result
        
        assertTrue(
          hasFileName,
          hasLineNumbers,
          hasOuter,
          hasInner
        ) ?? s"Stack trace should include file names and line numbers. Stack trace:\n$stackTrace"
      }
    },
    
    test("Comparison: LHS and RHS produce equivalent trace depth") {
      // Verify that after the fix, LHS and RHS have comparable trace information
      
      def lhsA(implicit trace: Trace): ZIO[Any, String, Unit] = 
        lhsB.flatMap(_ => ZIO.unit)
      
      def lhsB(implicit trace: Trace): ZIO[Any, String, Unit] = 
        lhsC.flatMap(_ => ZIO.unit)
      
      def lhsC(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.fail("LHS failure")

      def rhsA(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => rhsB)
      
      def rhsB(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.unit.flatMap(_ => rhsC)
      
      def rhsC(implicit trace: Trace): ZIO[Any, String, Unit] = 
        ZIO.fail("RHS failure")

      for {
        lhsExit <- lhsA.exit
        rhsExit <- rhsA.exit
        
        lhsResult <- lhsExit match {
          case Exit.Failure(cause) =>
            val trace = cause.prettyPrint
            val hasA = trace.contains("lhsA")
            val hasB = trace.contains("lhsB")
            val hasC = trace.contains("lhsC")
            ZIO.succeed((hasA, hasB, hasC, trace))
          case _ => ZIO.fail("Expected LHS failure")
        }
        
        rhsResult <- rhsExit match {
          case Exit.Failure(cause) =>
            val trace = cause.prettyPrint
            val hasA = trace.contains("rhsA")
            val hasB = trace.contains("rhsB")
            val hasC = trace.contains("rhsC")
            ZIO.succeed((hasA, hasB, hasC, trace))
          case _ => ZIO.fail("Expected RHS failure")
        }
      } yield {
        val (lhsHasA, lhsHasB, lhsHasC, lhsTrace) = lhsResult
        val (rhsHasA, rhsHasB, rhsHasC, rhsTrace) = rhsResult
        
        val lhsComplete = lhsHasA && lhsHasB && lhsHasC
        val rhsComplete = rhsHasA && rhsHasB && rhsHasC
        
        assertTrue(
          lhsComplete,
          rhsComplete
        ) ?? s"Both LHS and RHS should have complete traces.\nLHS complete: $lhsComplete\nRHS complete: $rhsComplete\n\nLHS trace:\n$lhsTrace\n\nRHS trace:\n$rhsTrace"
      }
    },
    
    test("Successful flatMap chains don't break") {
      // Ensure the fix doesn't break successful execution paths
      
      def a(implicit trace: Trace): ZIO[Any, Nothing, Int] = 
        ZIO.succeed(1).flatMap(_ => b)
      
      def b(implicit trace: Trace): ZIO[Any, Nothing, Int] = 
        ZIO.succeed(2).flatMap(_ => c)
      
      def c(implicit trace: Trace): ZIO[Any, Nothing, Int] = 
        ZIO.succeed(42)

      for {
        result <- a
      } yield assertTrue(result == 42)
    },
    
    test("Large flatMap chains are performant") {
      // Verify that the fix doesn't cause performance issues or stack explosions
      
      def buildChain(n: Int)(implicit trace: Trace): ZIO[Any, String, Int] =
        if (n <= 0) ZIO.fail("Chain end")
        else ZIO.unit.flatMap(_ => buildChain(n - 1))

      for {
        exit <- buildChain(100).exit
        result <- exit match {
          case Exit.Failure(cause) =>
            val stackTrace = cause.prettyPrint
            val hasChain = stackTrace.contains("buildChain(Issue8747Spec.scala:")
            ZIO.succeed((hasChain, stackTrace))
          case _ => ZIO.fail("Expected failure")
        }
      } yield {
        val (hasChain, stackTrace) = result
        
        // Verify that trace is captured and stack doesn't explode
        assertTrue(hasChain) ?? s"Large chains should capture traces without explosion. Stack trace:\n$stackTrace"
      }
    }
  )
}
