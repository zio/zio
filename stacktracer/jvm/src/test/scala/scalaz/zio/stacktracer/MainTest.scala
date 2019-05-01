package scalaz.zio.stacktracer

import org.specs2.Specification
import scalaz.zio.stacktracer.impl.AkkaExtractorImpl

class MainTest extends Specification {

  def is = s2"""
    main $mainTest
  """

  def mainTest = {
    Main.main()
    1 should_=== 1
  }

}

class X {
  def flatMap(f: X => X): X = {
    Main.run(f)
    f(this)
  }

  def map(f: X => X): X = {
    Main.run(f)
    f(this)
  }
}

object z {
  def x() = {
    val a = new X()
    val _ = for {
      v  <- a
      v1 <- v
      v2 <- v1
    } yield {
      v2
    }
  }
}

object Main {

  def akka(l: X => X) = {
    val start                                                   = System.nanoTime()
    val Some(SourceLocation(file, className, methodName, line)) = new AkkaExtractorImpl().extractSourceLocation(l)
    val end                                                     = System.nanoTime()

    System.err.println(s"  at $className.$methodName($file:$line) akka micros: ${(end - start) / 1000}")
  }

  def run(l: X => X): Option[Unit] = {
    akka(l)
//    new AsmExtractorImpl().extractSourceLocation(l, null)

    Some(())
  }

  //
//    val stackCaller = new RuntimeException().getStackTrace.init.last.getMethodName

  // reflection
//        val implclass = cl.loadClass(sl.getImplClass)
//        val method    = implclass.getMethods.find(_.getName == sl.getImplMethodName)
//        println(implclass -> method)

  def x(a: X): X = a

  def main(): Unit = {
    val f = new Function[X, X] {
      override def apply(v1: X): X = v1
    }

    (1 to 10000) foreach { _ =>
      run(a => a)
    }

    run(a => a)
    run(x)
    run(f) // points to f defn

    z.x()
  }
  // Scala 2.12 lambda names: $anonfun$main$2
  // Scala 2.11 class names: <Main$>$anonfun$main$1$$anonfun$apply$3
  // name-mangling: $eq$colon$eq
  //   at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 130
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 129
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 129
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 128
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 128
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 127
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 128
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 128
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 127
  //  at scalaz.zio.stacktracer.Main$$anonfun$main$1$$anonfun$apply$3.<anon_class>(LambdaExtractor.scala:162) akka micros: 128
}
