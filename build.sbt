ThisBuild / tlBaseVersion := "0.1"
ThisBuild / organization := "com.dwolla"
ThisBuild / organizationName := "Dwolla"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("bpholt", "Brian Holt")
)

ThisBuild / crossScalaVersions := Seq("2.13.18", "2.12.21", "3.3.7")
ThisBuild / scalaVersion := "2.13.18" // the default Scala
ThisBuild / githubWorkflowScalaVersions := Seq("2.13", "2.12", "3")
ThisBuild / tlJdkRelease := Some(8)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / mergifyStewardConfig ~= { _.map {
  _.withAuthor("dwolla-oss-scala-steward[bot]")
    .withMergeMinors(true)
}}

lazy val root = tlCrossRootProject.aggregate(
  core,
)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "skunk-retries",
    libraryDependencies ++= Seq(
      "org.tpolecat" %%% "skunk-core" % "0.6.4",
      "org.tpolecat" %%% "natchez-core" % "0.3.8",
      "org.typelevel" %%% "cats-tagless-core" % "0.16.3",
      "com.dwolla" %%% "natchez-tagless" % "0.2.6",
      "io.circe" %%% "circe-literal" % "0.14.15",
      "com.github.cb372" %%% "cats-retry" % (if (scalaVersion.value.startsWith("2") ) "3.1.3" else "4.0.0"),
    ),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.dwolla.buildinfo.skunkretries",
  )
