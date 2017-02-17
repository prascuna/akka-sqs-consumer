package com.github.prascuna.akkasqs.actors


import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, ActorSystem, Props}
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model._
import com.github.prascuna.akkasqs.settings.SqsSettings

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class SqsActor(sqsClient: AmazonSQSAsync,
               settings: SqsSettings,
               consumerFactory: (ActorRefFactory) => ActorRef
              )
              (implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import SqsActor._

  private val consumer = consumerFactory(context)


  private val queueUrl = settings.queueUrl.toString

  private val request = new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(settings.maxMessages)

  override def receive: Receive = {
    case SqsReceive =>
      val s = sender()
      log.debug("Receiving messages")
      sqsClient.receiveMessageAsync(request, new AsyncHandler[ReceiveMessageRequest, ReceiveMessageResult] {
        override def onError(exception: Exception): Unit =
          log.error(exception, "Error receiving messages")

        override def onSuccess(request: ReceiveMessageRequest, result: ReceiveMessageResult): Unit =
          result.getMessages.asScala.foreach { msg =>
            s ! SqsMessage(msg)
          }
      })

    case SqsDelete(message) =>
      sqsClient.deleteMessageAsync(queueUrl, message.getReceiptHandle, new AsyncHandler[DeleteMessageRequest, DeleteMessageResult] {
        override def onError(exception: Exception): Unit =
          log.error(exception, s"Error deleting Message[${message.getMessageId}]")


        override def onSuccess(request: DeleteMessageRequest, result: DeleteMessageResult): Unit =
          log.debug(s"Message [${message.getMessageId}] Deleted")
      })

    case SqsSend(messageBody) =>
      val s = sender()
      sqsClient.sendMessageAsync(queueUrl, messageBody, new AsyncHandler[SendMessageRequest, SendMessageResult] {
        override def onError(exception: Exception): Unit =
          log.error(exception, "Error sending Message")

        override def onSuccess(request: SendMessageRequest, result: SendMessageResult): Unit = {
          s ! SqsSent(result.getMessageId)
          log.debug(s"Message [${result.getMessageId}] Sent")
        }
      })

  }
}

object SqsActor {

  val name = "sqs-actor"

  def apply(sqsClient: AmazonSQSAsync,
            settings: SqsSettings,
            idempotentConsumerFactory: (ActorRefFactory) => ActorRef,
            name: String = name
           )
           (implicit ec: ExecutionContext, system: ActorSystem): ActorRef =
    system.actorOf(Props(new SqsActor(sqsClient, settings, idempotentConsumerFactory)), name)

  case object SqsReceive

  case class SqsDelete(message: Message)

  case class SqsSend(messageBody: String)

  case class SqsSent(messageId: String)

  case class SqsMessage(message: Message)

  case class SqsAck(message: Message)

}