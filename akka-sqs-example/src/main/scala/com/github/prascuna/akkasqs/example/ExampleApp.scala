package com.github.prascuna.akkasqs.example

import akka.actor.ActorSystem
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.github.prascuna.akkasqs.actors.{SqsActor, SqsIdempotentConsumer}
import com.github.prascuna.akkasqs.example.actors.SqsConsumer
import com.github.prascuna.akkasqs.services.MessageServiceRedis
import com.github.prascuna.akkasqs.settings.Settings
import com.redis.RedisClientPool
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging


object ExampleApp extends App with LazyLogging {

  val settings = Settings(ConfigFactory.load())

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  implicit val system = ActorSystem("example-system")

  val asyncClientBuilder = AmazonSQSAsyncClientBuilder.standard()
  asyncClientBuilder.setEndpointConfiguration(new EndpointConfiguration(settings.sqsSettings.queueUrl.toString, settings.sqsSettings.region))
  val sqsClient = asyncClientBuilder.build()

  val redisClientPool = new RedisClientPool("localhost", 6379)
  // Change this redis config
  val messageService = new MessageServiceRedis(settings, redisClientPool)

  val consumerFactory = SqsConsumer.factory(settings)
//
  val idempotentConsumerFactory = SqsIdempotentConsumer.factory(messageService, consumerFactory)
  val sqsActor = SqsActor(sqsClient, settings.sqsSettings, idempotentConsumerFactory)
  sys.addShutdownHook {
    system.terminate()
  }

}
