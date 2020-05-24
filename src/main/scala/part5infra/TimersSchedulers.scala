package part5infra

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props, Timers}

object TimersSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("SchedulersTimersDemo")
  import system.dispatcher

  val simpleActor = system.actorOf(Props[SimpleActor])

  //  system.log.info("Scheduling reminder for simple for simpleActor")

//  system.scheduler.scheduleOnce(1 second) {
//    simpleActor ! "reminder"
//  }
//
//  val routine: Cancellable = system.scheduler.schedule(1 second, 2 seconds) {
//    simpleActor ! "heartbeat"
//  }
//
//  system.scheduler.scheduleOnce(5 seconds) {
//    routine.cancel()
//  }

  /**
    * Exercise: implement a self-closing actor
    *
    * - if an actor receives a message (everything), you have 1 second to send it another message
    * - if the window expires, the actor will stop itself
    * - if you send another message, the window is rest
    */

  class SelfClosingActor extends Actor with ActorLogging {
    def receive: Receive = waiting(None)

    override def postStop(): Unit =
      log.warning("stopping actor")

    private def waiting(schedule: Option[Cancellable]): Receive = {
      case message =>
        log.info(s"received: $message")
        schedule.foreach { s =>
          log.info("canceling self-closing time window")
          s.cancel()
        }
        log.warning("starting self-closing time window")
        val nextSchedule = context.system.scheduler.scheduleOnce(1 seconds) {
          context.stop(self)
        }
        context.become(waiting(Some(nextSchedule)))
    }
  }

//  val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfClosingActor")
//
//  selfClosingActor ! "go!"
//
//  val routine = system.scheduler.scheduleOnce(250 millis) {
//    selfClosingActor ! "ping"
//  }
//
//  system.scheduler.scheduleOnce(2 seconds) {
//    system.log.info("sending pong to the self-closing actor")
//    selfClosingActor ! "pong"
//  }

  /**
    * Timer
    */

  case object TimerKey
  case object Start
  case object Reminder
  case object Stop
  class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
    timers.startSingleTimer(TimerKey, Start, 500 millis)
    def receive: Receive = {
      case Start =>
        log.info("Bootstrapping")
        timers.startPeriodicTimer(TimerKey, Reminder, 1 seconds)
      case Reminder =>
        log.info("I'm alive")
      case Stop =>
        log.warning("Stopping")
        timers.cancel(TimerKey)
        context.stop(self)
    }
  }

  val timerHeartbeatActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timerActor")
  system.scheduler.scheduleOnce(5 seconds) {
    timerHeartbeatActor ! Stop
  }



  

}
