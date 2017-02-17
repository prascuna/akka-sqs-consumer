package com.github.prascuna.akkasqs.actors

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.amazonaws.services.sqs.model.Message
import com.github.prascuna.akkasqs.actors.SqsActor.{SqsAck, SqsDelete, SqsMessage, SqsReceive}
import com.github.prascuna.akkasqs.services.MessageService
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class SqsIdempotentConsumerTest extends TestKit(ActorSystem("testSystem")) with ImplicitSender
  with FunSpecLike with BeforeAndAfterAll with Matchers with MockitoSugar with SqsIdempotentConsumerFixtures {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  describe("SqsIdempotentConsumer") {
    describe("when receiving a message") {
      describe("in any case") {
        val messageService = mock[MessageService]
        when(messageService.pending(message.getMessageId)) thenReturn Future.successful(true)
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(messageService, 5)
        val m = SqsMessage(message)

        it("should try mark the message status as PENDING") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
          verify(messageService).pending(message.getMessageId)
        }
      }
      describe("never received before") {
        val messageService = mock[MessageService]
        when(messageService.pending(message.getMessageId)) thenReturn Future.successful(true)
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(messageService, 1)
        val m = SqsMessage(message)

        it("should not reply anything to the sender") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
        }
        it("should send it to the sqsConsumer actor") {
          sqsIdempotentConsumer ! m
          sqsConsumerProbe.expectMsg(m)

        }
      }
      describe("previously received") {
        val messageService = mock[MessageService]
        when(messageService.pending(message.getMessageId)) thenReturn Future.successful(false)
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(messageService, 2)
        val m = SqsMessage(message)

        it("should not reply to the sender") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
        }
        it("should not send it to the sqsConsumer actor") {
          sqsConsumerProbe.expectNoMsg()
        }
      }
      describe("and the message service fails") {
        val messageService = mock[MessageService]
        when(messageService.pending(message.getMessageId)) thenReturn Future.failed(new RuntimeException("boo"))
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(messageService, 3)
        val m = SqsMessage(message)

        it("should not send it to the sqsConsumer actor") {
          sqsIdempotentConsumer ! m
          sqsConsumerProbe.expectNoMsg()
        }
      }
    }

    describe("when receiving an Ack for a message") {
      describe("in any case") {
        val messageService = mock[MessageService]
        when(messageService.processed(message.getMessageId)) thenReturn Future.successful(true)
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(messageService, 4)
        val m = SqsAck(message)

        it("should try to mark the message status as PROCESSED") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
          verify(messageService).processed(message.getMessageId)
        }
        it("should not reply to the sender") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
        }

      }
      describe("and it managed to mark the message as PROCESSED successfully") {
        val messageService = mock[MessageService]
        when(messageService.processed(message.getMessageId)) thenReturn Future.successful(true)
        val (sqsIdempotentConsumer, sqsConsumerProbe, sqsParentProbe) = createActorWithTestActorRef(messageService, 5)
        val m = SqsAck(message)
        it("should send an SqsDelete to sqsActor") {
          sqsIdempotentConsumer ! m
          sqsParentProbe.expectMsg(SqsDelete(message))
        }
      }
      describe("and it failed marking the message as PROCESSED") {
        val messageService = mock[MessageService]
        when(messageService.processed(message.getMessageId)) thenReturn Future.successful(false)
        val (sqsIdempotentConsumer, sqsConsumerProbe, sqsParentProbe) = createActorWithTestActorRef(messageService, 5)
        val m = SqsAck(message)
        it("should send an SqsDelete to sqsActor anyway") {
          sqsIdempotentConsumer ! m
          sqsParentProbe.expectMsg(SqsDelete(message))
        }
      }
      describe("and the message service fails") {
        val messageService = mock[MessageService]
        when(messageService.processed(message.getMessageId)) thenReturn Future.failed(new RuntimeException("boo"))
        val (sqsIdempotentConsumer, sqsConsumerProbe, sqsParentProbe) = createActorWithTestActorRef(messageService, 5)
        val m = SqsAck(message)
        it("should send an SqsDelete to sqsActor anyway") {
          sqsIdempotentConsumer ! m
          sqsParentProbe.expectMsg(SqsDelete(message))
        }
      }
    }

    describe("when receiving an SqsReceive") {
      val messageService = mock[MessageService]
      when(messageService.processed(message.getMessageId)) thenReturn Future.failed(new RuntimeException("boo"))
      val (sqsIdempotentConsumer, sqsConsumerProbe, sqsParentProbe) = createActorWithTestActorRef(messageService, 5)
      val m = SqsReceive
      it("should send it to the parent") {
        sqsIdempotentConsumer ! m
        sqsParentProbe.expectMsg(m)
      }
    }
  }


  def createActor(messageService: MessageService, actorNumber: Int): (ActorRef, TestProbe) = {

    // See here http://christopher-batey.blogspot.co.uk/2014/02/akka-testing-messages-sent-to-child.html
    val sqsConsumerProbe = TestProbe()
    val sqsConsumerFactory: (ActorRefFactory) => ActorRef = _ => sqsConsumerProbe.ref

    val factory = SqsIdempotentConsumer.factory(messageService, sqsConsumerFactory, SqsIdempotentConsumer.name + "-" + actorNumber)
    (factory(system), sqsConsumerProbe)
  }

  def createActorWithTestActorRef(messageService: MessageService, actorNumber: Int): (ActorRef, TestProbe, TestProbe) = {
    val sqsConsumerProbe = TestProbe()
    val parentProbe = TestProbe()
    val actor = TestActorRef(SqsIdempotentConsumer.props(messageService, _ => sqsConsumerProbe.ref), parentProbe.ref, SqsIdempotentConsumer.name + "-" + actorNumber)

    (actor, sqsConsumerProbe, parentProbe)
  }


}

trait SqsIdempotentConsumerFixtures {
  val message = new Message().withMessageId("testId1").withReceiptHandle("testReceiptHandle1").withBody("testBody1")
}