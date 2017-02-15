package com.github.prascuna.akkasqs.services

import scala.concurrent.Future

trait MessageService {

  import MessageStatus._

  def pending(messageId: String): Future[Boolean]

  def processed(messageId: String): Future[Boolean]

  def status(messageId: String): Future[Option[Status]]

}

object MessageStatus {

  sealed abstract class Status(val name: String) {
    override def toString: String = name

  }

  case object Pending extends Status("PENDING")

  case object Processed extends Status("PROCESSED")

  def fromString(name: String): Option[Status] =
    name match {
      case Pending.name => Some(Pending)
      case Processed.name => Some(Processed)
      case _ => None
    }

}