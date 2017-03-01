package com.example

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import cakesolutions.kafka.akka.KafkaConsumerActor.{Confirm, Unsubscribe}
import com.typesafe.config.Config

class PingActor(val config: Config, pipeTo: ActorRef) extends Actor
  with ActorLogging with PingPongConsumer with PingPongProducer{
  import PingActor._
  import PingPongProtocol._

  var counter = 0

  override def preStart() = {
    super.preStart()
    subscribe(topics)
  }

  override def postStop() = {
    kafkaConsumerActor ! Unsubscribe
    super.postStop()
  }

  def receive = playingPingPong

  def playingPingPong: Receive = {

    case pongExtractor(consumerRecords) =>

      consumerRecords.pairs.foreach {
        case (None, submitSampleCommand) =>
          log.error(s"Received unkeyed submit sample command: $submitSampleCommand")

        case (Some(id), pongMessage) =>
          submitMsg(PongActor.topics, PingPongMessage("pong"))
          counter += 1
          if (counter == 3) {
            pipeTo ! GameOver
          }

          // By committing *after* processing we get at-least-once-processing, but that's OK here because we can identify duplicates by their timestamps
          kafkaConsumerActor ! Confirm(consumerRecords.offsets)
          log.info(s"In PingActor - id:$id, msg: $pongMessage, offsets ${consumerRecords.offsets}")
      }

    case unknown =>
      log.error(s"got Unknown message: $unknown")
      pipeTo ! "unknown"
  }
}

object PingActor {
  def props(config: Config, pipeTo: ActorRef) = Props( new PingActor(config, pipeTo))
  val topics = List("ping")
  case object GameOver
}