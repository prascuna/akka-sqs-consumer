package com.github.prascuna.akkasqs.settings

import java.net.URL

import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}


case class SqsSettings(queueUrl: URL, maxMessages: Int, fetchingInterval: FiniteDuration, writeTimeout: FiniteDuration)


object SqsSettings {

  def apply(config: Config): SqsSettings = {
    SqsSettings(
      queueUrl = new URL(config.getString("aws.sqs.queueUrl")),
      maxMessages = config.getInt("aws.sqs.maxMessages"),
      fetchingInterval = config.getScalaFiniteDuration("aws.sqs.fetchingInterval"),
      writeTimeout = config.getScalaFiniteDuration("aws.sqs.sendMessageTimeout")
    )
  }

  implicit class RichConfig(config: Config) {

    //    def getScalaSet(path: String): Set[String] =
    //      config.getString(path).split(",").map(_.trim).toSet
    //
    def getScalaDuration(path: String): Duration =
    Duration(config.getString(path))

    def getScalaFiniteDuration(path: String): FiniteDuration =
      FiniteDuration(getScalaDuration(path).toSeconds, SECONDS)

  }


}
