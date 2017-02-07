package com.github.prascuna.akkasqs.actors

import java.net.URL

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model._
import com.github.prascuna.akkasqs.actors.SqsActor._
import com.github.prascuna.akkasqs.settings.SqsSettings
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class SqsActorTest extends TestKit(ActorSystem("testSystem")) with ImplicitSender
  with FunSpecLike with BeforeAndAfterAll with Matchers with MockitoSugar with SqsActorFixtures {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  describe("SqsActor") {


    describe("when told to receive messages") {
      val sqsClient = mock[AmazonSQSAsyncClient]
      val sqsActor = createSqsActor(settings, sqsClient, 1)
      type AsyncCallback = AsyncHandler[ReceiveMessageRequest, ReceiveMessageResult]
      when(sqsClient.receiveMessageAsync(any[ReceiveMessageRequest], any[AsyncCallback]))
        .thenAnswer((invocation: InvocationOnMock) => {
          val callback = invocation.getArgument[AsyncCallback](1)
          callback.onSuccess(mock[ReceiveMessageRequest], receiveMessageResult)
        })
      it("should send back the retrieved messages") {
        sqsActor ! SqsReceive
        expectMsgAllOf(SqsMessage(message1), SqsMessage(message2))
      }
    }

    describe("when told to delete a message") {
      val sqsClient = mock[AmazonSQSAsyncClient]
      val sqsActor = createSqsActor(settings, sqsClient, 2)
      type AsyncCallback = AsyncHandler[DeleteMessageRequest, DeleteMessageResult]
      when(sqsClient.deleteMessageAsync(ArgumentMatchers.eq(settings.queueUrl.toString), ArgumentMatchers.eq(message1.getReceiptHandle), any[AsyncCallback]))
        .thenAnswer((invocation: InvocationOnMock) => {
          val callback = invocation.getArgument[AsyncCallback](1)
          callback.onSuccess(mock[DeleteMessageRequest], mock[DeleteMessageResult])
        })
      it("should send a delete message to SQS") {
        sqsActor ! SqsDelete(message1)
        expectNoMsg()

        verify(sqsClient).deleteMessageAsync(ArgumentMatchers.eq(settings.queueUrl.toString), ArgumentMatchers.eq(message1.getReceiptHandle), any[AsyncCallback])
      }
    }

    describe("when told to send a messages") {
      val sqsClient = mock[AmazonSQSAsyncClient]
      type AsyncCallback = AsyncHandler[SendMessageRequest, SendMessageResult]
      when(sqsClient.sendMessageAsync(ArgumentMatchers.eq(settings.queueUrl.toString), ArgumentMatchers.eq("message1"), any[AsyncCallback]))
        .thenAnswer((invocation: InvocationOnMock) => {
          val callback = invocation.getArgument[AsyncCallback](2)
          callback.onSuccess(mock[SendMessageRequest], new SendMessageResult().withMessageId("messageId"))
        })
      val sqsActor = createSqsActor(settings, sqsClient, 3)
      it("should send the message to SQS and should reply to the sender with SqsSent(messageId)") {
        sqsActor ! SqsSend("message1")
        expectMsg(SqsSent("messageId"))

      }
    }
  }

  def createSqsActor(settings: SqsSettings, sqsClient: AmazonSQSAsyncClient, actorNumber: Int): ActorRef = {

    // See here http://christopher-batey.blogspot.co.uk/2014/02/akka-testing-messages-sent-to-child.html
    val probe = TestProbe()
    val factory: (ActorRefFactory => ActorRef) = _ => probe.ref

    val sqsActorFactory = SqsActor.factory(sqsClient, settings, factory, SqsActor.name + "-" + actorNumber)
    sqsActorFactory(system)
  }
}

trait SqsActorFixtures {
  val settings = SqsSettings(
    queueUrl = new URL("http://test"),
    maxMessages = 10,
    fetchingInterval = 10 seconds,
    writeTimeout = 20 seconds
  )
  val message1 = new Message().withMessageId("testId1").withReceiptHandle("testReceiptHandle1").withBody("testBody1")
  val message2 = new Message().withMessageId("testId2").withReceiptHandle("testReceiptHandle2").withBody("testBody2")
  val receiveMessageResult = new ReceiveMessageResult()
    .withMessages(message1)
    .withMessages(message2)

  val deleteMessageResult = new DeleteMessageResult

}
