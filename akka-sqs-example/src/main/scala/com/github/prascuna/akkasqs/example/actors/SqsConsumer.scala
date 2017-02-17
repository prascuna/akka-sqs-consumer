package com.github.prascuna.akkasqs.example.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.util.Timeout
import com.github.prascuna.akkasqs.actors.SqsActor._
import com.github.prascuna.akkasqs.settings.Settings

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class SqsConsumer(settings: Settings)(implicit ec: ExecutionContext, system: ActorSystem) extends Actor with ActorLogging {

  private val sqsActor = context.parent //It's actually pointing the IdempotentConsumer proxy

  private implicit val timeout = Timeout(settings.sqsSettings.writeTimeout)

  system.scheduler.schedule(0 seconds, settings.sqsSettings.fetchingInterval, sqsActor, SqsReceive)


  override def receive: Receive = {
    case SqsMessage(message) =>
      log.debug(s"Message [${message.getMessageId}] processing ")
      // Do something with the message
      if (message.getBody == "reply") {
        sqsActor ! SqsSend("This is a test message")
      }
      // Done with processing, I can ack the message, that will be deleted
      sqsActor ! SqsAck(message)

    case SqsSent(messageId) =>
      log.debug(s"Message [$messageId] has been sent")
  }
}

object SqsConsumer {
  val name = "sqs-consumer"

  def factory(settings: Settings,
              name: String = name)
             (implicit ec: ExecutionContext, system: ActorSystem): (ActorRefFactory => ActorRef) =
    _.actorOf(props(settings), name)

  def props(settings: Settings)
           (implicit ex: ExecutionContext, system: ActorSystem): Props = {
    Props(new SqsConsumer(settings))
  }

}