package com.github.prascuna.akkasqs.services

import com.github.prascuna.akkasqs.settings.Settings
import com.redis.{RedisClient, RedisClientPool, Seconds}

import scala.concurrent.{ExecutionContext, Future}

class MessageServiceRedis(settings: Settings, redisClients: RedisClientPool)(implicit ec: ExecutionContext) extends MessageService {

  import MessageStatus._

  private def redisKey(messageId: String) = s"msg.$messageId.status"

  override def pending(messageId: String): Future[Boolean] =
    asyncRedisOp(redisClients)(
      _.set(redisKey(messageId), Pending.toString, onlyIfExists = false, Seconds(settings.idempotencySettings.pendingMessageTTL.toSeconds))
    )

  override def processed(messageId: String): Future[Boolean] =
    asyncRedisOp(redisClients)(
      _.setex(redisKey(messageId), settings.idempotencySettings.processedMessageTTL.toSeconds, Processed.toString)
    )

  override def status(messageId: String): Future[Option[Status]] =
    asyncRedisOp(redisClients)(
      _.get(redisKey(messageId)).flatMap(k => MessageStatus.fromString(k))
    )

  private def asyncRedisOp[T](redisClientPool: RedisClientPool)(body: RedisClient => T): Future[T] =
    Future {
      redisClientPool.withClient { redis =>
        body(redis)
      }
    }

}
