package part3testing

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}

import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class BasicSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  import BasicSpec._

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val message = "hello, test"
      echoActor ! message
      expectMsg(message) // akka.test.single-expect-default
      testActor
    }
  }

  "A blackhole actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[BlackHole])
      val message = "hello, test"
      echoActor ! message
      expectNoMessage(1 second)
    }
  }

  // message assertions

  "A lab test actor" should {
    val labTestActor = system.actorOf(Props[LabTestActor])

    "turn a string into uppercase" in {
      labTestActor ! "I love Akka"
      val reply = expectMsgType[String]
      assert(reply == "I LOVE AKKA")
    }

    "reply to a greeting" in {
      labTestActor ! "greeting"
      expectMsgAnyOf("hi", "hello")
    }

    "reply with favorite tech" in {
      labTestActor ! "favoriteTech"
      expectMsgAllOf("Scala", "Akka")
    }

    "reply with cool tech in a different way" in  {
      labTestActor ! "favoriteTech"
      val messages = receiveN(2) // Seq[AnyRef]
      // free to do more complicated assertions
    }

    "reply with cool tech in a fancy way" in {
      labTestActor ! "favoriteTech"
      expectMsgPF() {
        case "Scala" => // only care if the PF is defined
        case "Akka" =>
      }
    }
  }

}

object BasicSpec {

  class SimpleActor extends Actor {
    def receive: Receive = {
      case message => sender() ! message
    }
  }

  class BlackHole extends Actor {
    def receive: Receive = Actor.emptyBehavior
  }

  class LabTestActor extends Actor {
    val random = new Random()

    def receive: Receive = {
      case "greeting" =>
        if (random.nextBoolean()) sender() ! "hi"
        else sender() ! "hello"
      case "favoriteTech" =>
        sender() ! "Scala"
        sender() ! "Akka"
      case message: String => sender() ! message.toUpperCase
    }
  }
  
}