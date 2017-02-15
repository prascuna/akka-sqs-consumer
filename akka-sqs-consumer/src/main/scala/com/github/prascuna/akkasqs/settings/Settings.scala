package com.github.prascuna.akkasqs.settings

import java.net.URL

import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}

case class Settings(sqsSettings: SqsSettings, idempotencySettings: IdempotencySettings)

case class SqsSettings(queueUrl: URL, maxMessages: Int, fetchingInterval: FiniteDuration, writeTimeout: FiniteDuration)

case class IdempotencySettings(pendingMessageTTL: FiniteDuration, processedMessageTTL: FiniteDuration)


object Settings {

  def apply(config: Config): Settings = Settings(
    sqsSettings = SqsSettings(
      queueUrl = new URL(config.getString("aws.sqs.queueUrl")),
      maxMessages = config.getInt("aws.sqs.maxMessages"),
      fetchingInterval = config.getScalaFiniteDuration("aws.sqs.fetchingInterval"),
      writeTimeout = config.getScalaFiniteDuration("aws.sqs.sendMessageTimeout")
    ),
    idempotencySettings = IdempotencySettings(
      pendingMessageTTL = config.getScalaFiniteDuration("report.ttl.pendingMessage"),
      processedMessageTTL = config.getScalaFiniteDuration("report.ttl.processedMessage")
    )
  )

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
