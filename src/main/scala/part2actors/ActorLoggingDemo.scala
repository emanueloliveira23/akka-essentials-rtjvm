package part2actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object ActorLoggingDemo extends App {

  // method nº 1 - explicit

  class SimpleActorWithExplicitLogging extends Actor {
    val logger = Logging(context.system, this)
    def receive: Receive = {
      case message =>logger.info(message.toString)
    }
  }

  val system = ActorSystem("LoggingDemo")
  val logger = system.actorOf(Props[SimpleActorWithExplicitLogging], "logger")

  logger ! "Logging a simple message"

  // method nº 2 - ActorLogging

  class ActorWithLogging extends Actor with ActorLogging {
    def receive: Receive = {
      case (a, b) => log.info("Two things: {} and {}", a, b)
      case message => log.info(message.toString)
    }
  }

  val logger2 = system.actorOf(Props[ActorWithLogging], "logger2")
  logger2 ! "Logging a simple message by extending a trait"
  logger2 ! ("a", 42)

}
