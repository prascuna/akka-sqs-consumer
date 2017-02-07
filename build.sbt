name := """akka-sqs-consumer"""

version := "1.0"


lazy val commonSettings = Seq(
  organization := "akka-sqs-consumer",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.12.1", "2.11.8")
)


lazy val core = (project in file("akka-sqs-consumer"))
  .settings(commonSettings: _*)
  .settings {
    name := "akka-sqs-consumer"
    libraryDependencies ++= {
      val awsJavaSdkVersion = "1.11.86"
      val akkaVersion = "2.4.16"
      Seq(
        "com.amazonaws" % "aws-java-sdk-sqs" % awsJavaSdkVersion,
        "com.amazonaws" % "aws-java-sdk-core" % awsJavaSdkVersion,
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
        "org.scalatest" %% "scalatest" % "3.0.1" % "test",
        "org.mockito" % "mockito-core" % "2.7.1" % "test"
      )
    }
  }

lazy val idempotentRedis = (project in file("akka-sqs-redis"))
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings {
    name := "akka-sqs-redis"
    libraryDependencies ++= Seq(
      "net.debasishg" %% "redisclient" % "3.3"
    )
  }

lazy val root = (project in file("."))
  .aggregate(core, idempotentRedis)
  .settings(commonSettings: _*)
  .settings {
    publishArtifact := false
  }