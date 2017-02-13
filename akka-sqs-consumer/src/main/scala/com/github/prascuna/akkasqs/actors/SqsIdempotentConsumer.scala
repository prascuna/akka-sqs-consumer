package com.github.prascuna.akkasqs.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import com.github.prascuna.akkasqs.services.MessageService

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class SqsIdempotentConsumer(messageService: MessageService,
                            sqsConsumerFactory: (ActorRefFactory) => ActorRef)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import SqsActor._

  private val sqsConsumer = sqsConsumerFactory(context)

  private val sqsActor = context.parent

  override def receive: Receive = {
    case m@SqsMessage(message) =>
      log.debug(s"Message [${message.getMessageId}] Incoming\n\n$message\n")
      messageService.pending(message.getMessageId)
        .onComplete {
          case Success(notSeen) if notSeen =>
            log.debug(s"Message [${message.getMessageId}] Is new")
            sqsConsumer ! m
          case Success(notSeen) if !notSeen =>
            log.warning(s"Message[${message.getMessageId}] Is duplicate. Skipping.")
          case Failure(e) =>
            log.error(e, "Error during idempotence check. Skipping message since it will be processed later from SQS")
        }
    case SqsAck(message) =>
      messageService.processed(message.getMessageId)
        .onComplete {
          case Success(processed) =>
            if (processed) {
              log.debug(s"Message [${message.getMessageId}] marked as processed. Deleting")
            } else {
              log.warning(s"Message [${message.getMessageId}] NOT marked as processed due to a Redis problem. Deleting anyway")
            }
            sqsActor ! SqsDelete(message)
          case Failure(e) =>
            log.error(e, s"Message [${message.getMessageId}] NOT marked as processed due to a Redis problem. Deleting anyway")
            sqsActor ! SqsDelete(message)
        }
    case SqsReceive =>
      sqsActor ! SqsReceive

    case message if sender() != sqsActor =>
      sqsActor forward message
  }

}

object SqsIdempotentConsumer {
  val name = "sqs-idempotent-consumer"

  def factory(messageService: MessageService,
              sqsConsumerFactory: (ActorRefFactory) => ActorRef,
              name: String = name
             )(implicit ec: ExecutionContext): (ActorRefFactory) => ActorRef =
    _.actorOf(probe(messageService, sqsConsumerFactory), name)

  def probe(messageService: MessageService,
            sqsConsumerFactory: (ActorRefFactory) => ActorRef)
           (implicit ec: ExecutionContext): Props = {
    Props(new SqsIdempotentConsumer(messageService, sqsConsumerFactory))
  }
}

