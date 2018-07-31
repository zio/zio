import Scalaz._

organization in ThisBuild := "org.scalaz"

version in ThisBuild := "0.1.0-SNAPSHOT"

resolvers += "Sonatype OSS Snapshots".at("https://oss.sonatype.org/content/repositories/snapshots")
publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots".at(nexus + "content/repositories/snapshots"))
  else
    Some("releases".at(nexus + "service/local/staging/deploy/maven2"))
}

dynverSonatypeSnapshots in ThisBuild := true

lazy val sonataCredentials = for {
  username <- sys.env.get("SONATYPE_USERNAME")
  password <- sys.env.get("SONATYPE_PASSWORD")
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

credentials in ThisBuild ++= sonataCredentials.toSeq

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val root =
  (project in file("."))
    .settings(stdSettings("ioqueue"))
    .settings(
      libraryDependencies ++= {

        val Zio = Seq(
          "org.scalaz" %% "scalaz-zio" % "0.1-SNAPSHOT"
        )

        val Specs2 = Seq(
          "org.specs2" %% "specs2-core"          % "4.3.2" % Test,
          "org.specs2" %% "specs2-scalacheck"    % "4.3.2" % Test,
          "org.specs2" %% "specs2-matcher-extra" % "4.3.2" % Test
        )

        Zio ++ Specs2
      },
      scalacOptions in Test ++= Seq("-Yrangepos")
    )
