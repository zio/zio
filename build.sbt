// shadow sbt-scalajs' crossProject from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import Scalaz._
import xerial.sbt.Sonatype._

organization in ThisBuild := "org.scalaz"
sonatypeProfileName := "org.scalaz"

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle in ThisBuild := true
licenses in ThisBuild := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
homepage in ThisBuild := Some(url("https://scalaz.github.io/scalaz-zio/"))
developers in ThisBuild := List(
  Developer(id = "jdegoes", name = "John De Goes", url = url("http://degoes.net"), email = "john@degoes.net")
)

dynverSonatypeSnapshots in ThisBuild := true
isSnapshot in ThisBuild := false

lazy val sonataCredentials = for {
  username <- sys.env.get("SONATYPE_USERNAME")
  password <- sys.env.get("SONATYPE_PASSWORD")
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

pgpPassphrase in Global := sys.env.get("PGP_PASSPHRASE").map(_.toArray)
pgpSecretRing in Global := sys.env
  .get("PGP_SECRING")
  .map(file)
  .getOrElse(baseDirectory.value / "project" / "secring.gpg")
pgpPublicRing in Global := sys.env
  .get("PGP_PUBRING")
  .map(file)
  .getOrElse(baseDirectory.value / "project" / "pubring.gpg")
useGpg in Global := false
pgpSigningKey in Global := Some(-6073725311241139476L) // "ABB5CB02682C4AEC"

credentials in ThisBuild ++= sonataCredentials.toSeq

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true,
    console := (console in Compile in coreJVM).value
  )
  .aggregate(coreJVM, coreJS, interopJVM, interopJS, interopCatsLaws, benchmarks, microsite)
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(stdSettings("zio"))
  .settings(
    libraryDependencies ++= Seq("org.specs2" %%% "specs2-core"          % "4.3.2" % Test,
                                "org.specs2" %%% "specs2-scalacheck"    % "4.3.2" % Test,
                                "org.specs2" %%% "specs2-matcher-extra" % "4.3.2" % Test),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "scalaz.zio",
    buildInfoObject := "BuildInfo"
  )

lazy val coreJVM = core.jvm
  .settings(
    // In the repl most warnings are useless or worse.
    // This is intentionally := as it's more direct to enumerate the few
    // options we do want than to try to subtract off the ones we don't.
    // One of -Ydelambdafy:inline or -Yrepl-class-based must be given to
    // avoid deadlocking on parallel operations, see
    //   https://issues.scala-lang.org/browse/SI-9076
    scalacOptions in Compile in console := Seq(
      "-Ypartial-unification",
      "-language:higherKinds",
      "-language:existentials",
      "-Yno-adapted-args",
      "-Xsource:2.13",
      "-Yrepl-class-based"
    ),
    initialCommands in Compile in console := """
                                               |import scalaz._
                                               |import scalaz.zio._
                                               |import scalaz.zio.console._
                                               |object replRTS extends RTS {}
                                               |import replRTS._
                                               |implicit class RunSyntax[E, A](io: IO[E, A]){ def unsafeRun: A = replRTS.unsafeRun(io) }
    """.stripMargin
  )

lazy val coreJS = core.js

lazy val interop = crossProject(JSPlatform, JVMPlatform)
  .in(file("interop"))
  .settings(stdSettings("zio-interop"))
  .dependsOn(core % "test->test;compile->compile")
  .settings(
    libraryDependencies ++= Seq(
      "org.scalaz"    %%% "scalaz-core"               % "7.2.+"  % Optional,
      "org.typelevel" %%% "cats-effect"               % "0.10.1" % Optional,
      "org.scalaz"    %%% "scalaz-scalacheck-binding" % "7.2.+"  % Test,
      "co.fs2"        %%% "fs2-core"                  % "0.10.5" % Test
    ),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

lazy val interopJVM = interop.jvm

lazy val interopJS = interop.js

// Separated due to binary incompatibility in scalacheck 1.13 vs 1.14
// TODO remove it when https://github.com/typelevel/discipline/issues/52 is closed
lazy val interopCatsLaws = project.module
  .in(file("interop-cats-laws"))
  .settings(stdSettings("zio-interop-cats-laws"))
  .dependsOn(coreJVM % "test->test", interopJVM % "test->compile")
  .settings(
    skip in publish := true,
    libraryDependencies ++= Seq(
      "org.typelevel"              %% "cats-effect-laws"          % "0.10.1" % Test,
      "org.typelevel"              %% "cats-testkit"              % "1.2.0"  % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.8"  % Test
    ),
    dependencyOverrides += "org.scalacheck" %% "scalacheck" % "1.13.5" % Test,
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

lazy val benchmarks = project.module
  .dependsOn(coreJVM)
  .enablePlugins(JmhPlugin)
  .settings(
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "org.scala-lang" % "scala-reflect"  % scalaVersion.value,
        "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided,
        "io.monix"       %% "monix"         % "3.0.0-RC1",
        "org.typelevel"  %% "cats-effect"   % "1.0.0-RC2"
      )
  )

lazy val microsite = project.module
  .dependsOn(coreJVM)
  .enablePlugins(MicrositesPlugin)
  .settings(
    scalacOptions -= "-Yno-imports",
    scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") },
    scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") },
    skip in publish := true,
    libraryDependencies ++= Seq(
      "com.github.ghik" %% "silencer-lib" % "1.0",
      "commons-io"      % "commons-io"    % "2.6"
    ),
    micrositeFooterText := Some(
      """
        |<p>&copy; 2018 <a href="https://github.com/scalaz/scalaz-zio">ZIO Maintainers</a></p>
        |""".stripMargin
    ),
    micrositeName := "ZIO",
    micrositeDescription := "ZIO",
    micrositeAuthor := "ZIO contributors",
    micrositeOrganizationHomepage := "https://github.com/scalaz/scalaz-zio",
    micrositeGitterChannelUrl := "scalaz/scalaz-zio",
    micrositeGitHostingUrl := "https://github.com/scalaz/scalaz-zio",
    micrositeGithubOwner := "scalaz",
    micrositeGithubRepo := "scalaz-zio",
    micrositeFavicons := Seq(microsites.MicrositeFavicon("favicon.png", "512x512")),
    micrositeDocumentationUrl := s"https://javadoc.io/doc/org.scalaz/scalaz-zio_2.12/${(version in Compile).value}",
    micrositeDocumentationLabelDescription := "Scaladoc",
    micrositeBaseUrl := "/scalaz-zio",
    micrositePalette := Map(
      "brand-primary"   -> "#ED2124",
      "brand-secondary" -> "#251605",
      "brand-tertiary"  -> "#491119",
      "gray-dark"       -> "#453E46",
      "gray"            -> "#837F84",
      "gray-light"      -> "#E3E2E3",
      "gray-lighter"    -> "#F4F3F4",
      "white-color"     -> "#FFFFFF"
    )
  )
