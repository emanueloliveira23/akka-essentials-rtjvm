package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App {

  /*
    ResourceActor
      - open => it can receive read/write requests to the resource
      - otherwise it will postpone all read/write requests until state is open

      ResourceActor is closed
        - Open => switch to open state
        - Read, Write messages are postpone

      ResourceActor is open
        - Read, Write are handled
        - Close => switch to closed state

      [Open, Read, Read, Write]
      - switch to the open state
      - read the data
      - read the data again
      - write the data

      [Read, Open, Write]
      - stash read the data
        Stash: [Read]
      - open => switch to open state
        Mailbox: [Read, Write]
      - read the data
      - write the data
   */

  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // step 1 - mixin the Stash trait
  class ResourceActor extends Actor with ActorLogging with Stash {
    private var innerData: String = ""
    def receive: Receive = closed
    private def closed: Receive = {
      case Open =>
        log.info("Opening resource")
        // step 5 - unstashAll when you switch the message handler
        unstashAll()
        context.become(open)
      case message =>
        log.info(s"stashing $message because I can't handle it in the closed state")
        // step 2 - stash away what you can't handle
        stash()
    }
    private def open: Receive = {
      case Read =>
        // do some actual computation
        log.info(s"I have read $innerData")
      case Write(data) =>
        log.info(s"I'm writing $data")
        innerData = data
      case Close =>
        log.info("Closing resource")
        unstashAll()
        context.become(closed)
      case message =>
        log.info(s"stashing $message because I can't handle it in the open state")
        // step 2 - stash away what you can't handle
        stash()
    }
  }

  val system = ActorSystem("StashDemo")
  val resourceActor = system.actorOf(Props[ResourceActor])

//  resourceActor ! Write("I love stash")
//  resourceActor ! Read
//  resourceActor ! Open

  resourceActor ! Read // stash
  resourceActor ! Open // switch open; I have read ""
  resourceActor ! Open // stash
  resourceActor ! Write("I love stash") // I'm writing I love stash
  resourceActor ! Close // switch to closed; switch to open
  resourceActor ! Read // I have read I love stash

}
