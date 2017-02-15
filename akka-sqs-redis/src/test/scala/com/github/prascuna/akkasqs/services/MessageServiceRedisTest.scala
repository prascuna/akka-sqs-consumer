package com.github.prascuna.akkasqs.services

import com.github.prascuna.akkasqs.services.MessageStatus.{Pending, Status}
import com.github.prascuna.akkasqs.settings.{IdempotencySettings, Settings}
import com.redis.serialization.{Format, Parse}
import com.redis.{RedisClient, RedisClientPool, Seconds}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncFunSpec, Matchers}

import scala.concurrent.duration._

class MessageServiceRedisTest extends AsyncFunSpec with Matchers with MockitoSugar with MessageServiceRedisTestFixtures {

  describe("MessageServiceRedis") {
    val settings = mock[Settings]
    when(settings.idempotencySettings) thenReturn idempotencySettings
    describe("when setting the status to pending") {
      describe("if the messageId is not marked yet") {
        val redisClient = mock[RedisClient]
        when(redisClient.set(anyString, anyString, anyBoolean(), any[Seconds])) thenReturn true
        val redisClientPool: RedisClientPool = mockRedisClientPool(redisClient)

        val messageService = new MessageServiceRedis(settings, redisClientPool)
        it("should mark the message and eventually return true") {
          messageService.pending("messageId").map { result =>
            verify(redisClient).set("msg.messageId.status", "PENDING", false, Seconds(idempotencySettings.pendingMessageTTL.toSeconds))
            result shouldBe true
          }
        }
      }
      describe("if the messageId is already marked") {
        val redisClient = mock[RedisClient]
        when(redisClient.set(anyString, anyString, anyBoolean(), any[Seconds])) thenReturn false
        val redisClientPool: RedisClientPool = mockRedisClientPool(redisClient)

        val messageService = new MessageServiceRedis(settings, redisClientPool)
        it("should not mark the message and eventually return false") {
          messageService.pending("messageId").map { result =>
            verify(redisClient).set("msg.messageId.status", "PENDING", false, Seconds(idempotencySettings.pendingMessageTTL.toSeconds))
            result shouldBe false
          }
        }
      }
    }
    describe("when setting the status to processed") {
      describe("if the messageId is not marked yet") {
        val redisClient = mock[RedisClient]
        when(redisClient.setex(anyString(), anyLong(), any[Any]())(any[Format])) thenReturn true
        val redisClientPool: RedisClientPool = mockRedisClientPool(redisClient)

        val messageService = new MessageServiceRedis(settings, redisClientPool)
        it("should mark the message and eventually return true") {
          messageService.processed("messageId").map { result =>
            verify(redisClient).setex("msg.messageId.status", idempotencySettings.processedMessageTTL.toSeconds, "PROCESSED")
            result shouldBe true
          }
        }
      }
      describe("if the messageId is already marked") {
        val redisClient = mock[RedisClient]
        when(redisClient.setex(anyString, anyLong(), any[Any]())(any[Format])) thenReturn false
        val redisClientPool: RedisClientPool = mockRedisClientPool(redisClient)

        val messageService = new MessageServiceRedis(settings, redisClientPool)
        it("should not mark the message and eventually return false") {
          messageService.processed("messageId").map { result =>
            verify(redisClient).setex("msg.messageId.status", idempotencySettings.processedMessageTTL.toSeconds, "PROCESSED")
            result shouldBe false
          }
        }
      }
    }
    describe("when retrieving the status") {
      describe("if the messageId is not marked yet") {
        val redisClient = mock[RedisClient]

        when(redisClient.get(anyString)(any[Format], any[Parse[String]])) thenReturn None
        val redisClientPool: RedisClientPool = mockRedisClientPool(redisClient)
        val messageService = new MessageServiceRedis(settings, redisClientPool)
        it("should return None") {
          messageService.status("messageId").map { status =>
            verify(redisClient).get("msg.messageId.status")
            status shouldBe None
          }
        }
      }
      describe("if the messageId is already marked") {
        val redisClient = mock[RedisClient]

        when(redisClient.get(anyString)(any[Format], any[Parse[String]])) thenReturn Some("PENDING")
        val redisClientPool: RedisClientPool = mockRedisClientPool(redisClient)
        val messageService = new MessageServiceRedis(settings, redisClientPool)
        it("should return the status") {
          messageService.status("messageId").map { status =>
            verify(redisClient).get("msg.messageId.status")
            status shouldBe Some(Pending)
          }
        }

      }
    }
  }

  private def mockRedisClientPool[T](redisClient: RedisClient) = {
    val redisClientPool = mock[RedisClientPool]
    when(redisClientPool.withClient(any[(RedisClient) => T]()))
      .thenAnswer((invocation: InvocationOnMock) => {
        val f = invocation.getArgument[(RedisClient) => T](0)
        f(redisClient)
      })
    redisClientPool
  }
}

trait MessageServiceRedisTestFixtures {
  val idempotencySettings = IdempotencySettings(10 seconds, 10 minutes)
}