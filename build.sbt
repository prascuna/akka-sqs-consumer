name := """akka-sqs-consumer"""

version := "1.0"

scalaVersion := "2.12.1"

crossScalaVersions := Seq("2.12.1","2.11.8")

// Change this to another test framework if you prefer
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