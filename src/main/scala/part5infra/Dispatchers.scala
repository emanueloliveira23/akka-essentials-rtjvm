package part5infra

import scala.concurrent.{ExecutionContext, Future}
//import scala.util.Random

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

import com.typesafe.config.ConfigFactory

object Dispatchers extends App {

  class Counter extends Actor with ActorLogging {
    var count = 0
    def receive: Receive = {
      case message =>
        count += 1
        log.info(s"[$count] $message")
    }
  }

  val system = ActorSystem("DispatchersDemo", ConfigFactory.load().getConfig("dispatchersDemo"))

  // method #1 - programmatically (in code)
//  val actors = for (i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")
//  val r = new Random()
//  for (i <- 1 to 1000) {
//    actors(r.nextInt(10)) ! i
//  }

  // method #2 - from config
  val rtjvm = system.actorOf(Props[Counter], "rtjvm")

  /**
    * Dispatchers implement the ExecutionContext trait
    */

  class DBActor extends Actor with ActorLogging {
    // solution #1
    implicit val ec: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher")
    // solution #2 - use a Router

    def receive: Receive = {
      case message => Future {
        // wait on a resources
        Thread.sleep(5000)
        log.info(s"Success: $message")
      }
    }
  }

  val dbActor = system.actorOf(Props[DBActor])
  val nonBlockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val message = s"important message $i"
    dbActor ! message
    nonBlockingActor ! message
  }

}
