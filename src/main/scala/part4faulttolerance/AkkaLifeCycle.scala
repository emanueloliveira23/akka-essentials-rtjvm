package part4faulttolerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

object AkkaLifeCycle extends App {

//  case object StartChild
//  class LifeCycleActor extends Actor with ActorLogging {
//
//    override def preStart(): Unit = log.info("I'm starting")
//    override def postStop(): Unit = log.info("I have stopped")
//
//    def receive: Receive = {
//      case StartChild =>
//        context.actorOf(Props[LifeCycleActor], "child")
//    }
//  }
//
  val system = ActorSystem("LifeCycleDemo")
//  val parent = system.actorOf(Props[LifeCycleActor], "parent")
//  parent ! StartChild
//  parent ! PoisonPill
//
  /**
    * restart
    */
  case object FailChild
  case object Fail
  case object CheckChild
  case object Check

  class Parent extends Actor {
    val child = context.actorOf(Props[Child], "supervisedChild")
    def receive: Receive = {
      case FailChild => child ! Fail
      case CheckChild => child ! Check
    }
  }
  class Child extends Actor with ActorLogging {
    override def preStart(): Unit = log.info("supervised child start")
    override def postStop(): Unit = log.info("supervised child stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.info(s"supervised actor restarting because of ${reason.getMessage}")
    }

    override def postRestart(reason: Throwable): Unit = {
      log.info("supervised actor restarted")
    }

    def receive: Receive = {
      case Fail =>
        log.warning("Child will fail now")
        throw new RuntimeException("I failed")
      case Check =>
        log.info("alive and kicking")
    }
  }

  val supervisor = system.actorOf(Props[Parent], "supervisor")
  supervisor ! FailChild
  supervisor ! CheckChild
}
