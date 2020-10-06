package zio.test.junit

import java.io.File

import org.apache.maven.cli.MavenCli
import zio.blocking.{ Blocking, effectBlocking }
import zio.test.Assertion._
import zio.test.{ DefaultRunnableSpec, _ }
import zio.{ RIO, ZIO }

import scala.xml.XML

/**
 * when running from IDE run `sbt publishM2`, copy the snapshot version the artifacts were published under (something like: `1.0.2+0-37ee0765+20201006-1859-SNAPSHOT`)
 * and put this into `VM Parameters`: `-Dproject.dir=$PROJECT_DIR$/test-junit-tests/jvm -Dproject.version=$snapshotVersion`
 */
object MavenJunitSpec extends DefaultRunnableSpec {

  def spec = suite("MavenJunitSpec")(
    testM("FailingSpec results are properly reported") {
      for {
        mvn       <- makeMaven
        mvnResult <- mvn.clean() *> mvn.test()
        report    <- mvn.parseSurefireReport("zio.test.junit.maven.FailingSpec")
      } yield {
        assert(mvnResult)(not(equalTo(0))) &&
        assert(report)(
          containsFailure("should fail", "zio.test.junit.TestFailed: 11 did not satisfy equalTo(12)") &&
            containsFailure(
              "should fail - isSome",
              "zio.test.junit.TestFailed: \n11 did not satisfy equalTo(12)\nSome(11) did not satisfy isSome(equalTo(12))"
            ) &&
            containsSuccess("should succeed")
        )
      }
    }
  ) @@ TestAspect.sequential

  def makeMaven: ZIO[Any, AssertionError, MavenDriver] = for {
    projectDir <-
      ZIO
        .fromOption(sys.props.get("project.dir"))
        .orElseFail(
          new AssertionError(
            "Missing project.dir system property\n" +
              "when running from IDE put this into `VM Parameters`: `-Dproject.dir=$PROJECT_DIR$/test-junit-tests/jvm`"
          )
        )
    projectVer <-
      ZIO
        .fromOption(sys.props.get("project.version"))
        .orElseFail(
          new AssertionError(
            "Missing project.version system property\n" +
              "when running from IDE put this into `VM Parameters`: `-Dproject.dir=<current zio version>`"
          )
        )
  } yield new MavenDriver(projectDir, projectVer)

  class MavenDriver(projectDir: String, projectVersion: String) {
    private val mvnRoot = new File(s"$projectDir/../maven").getCanonicalPath
    private val cli     = new MavenCli
    System.setProperty("maven.multiModuleProjectDirectory", mvnRoot)

    def clean(): RIO[Blocking, Int] = run("clean")
    def test(): RIO[Blocking, Int]  = run("test", s"-Dzio.version=${projectVersion}", s"-ssettings.xml")
    def run(command: String*): RIO[Blocking, Int] = effectBlocking(
      cli.doMain(command.toArray, mvnRoot, System.out, System.err)
    )

    def parseSurefireReport(testFQN: String) =
      effectBlocking(
        XML.load(scala.xml.Source.fromFile(new File(s"$mvnRoot/target/surefire-reports/TEST-$testFQN.xml")))
      ).map { report =>
        (report \ "testcase").map { tcNode =>
          TestCase(
            tcNode \@ "name",
            (tcNode \ "error").headOption.map(error => TestError(error.text.trim, error \@ "type"))
          )
        }
      }

  }

  def containsSuccess(label: String) = containsResult(label, error = None)
  def containsFailure(label: String, error: String) = containsResult(label, Some(error))
  def containsResult(label: String, error: Option[String]): Assertion[Iterable[TestCase]] =
    contains(TestCase(label, error.map(TestError(_, "zio.test.junit.TestFailed"))))

  case class TestCase(name: String, error: Option[TestError])
  case class TestError(message: String, `type`: String)
}
