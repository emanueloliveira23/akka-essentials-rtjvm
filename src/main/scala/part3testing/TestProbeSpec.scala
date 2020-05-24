package part3testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}

import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class TestProbeSpec extends TestKit(ActorSystem("TestProbeSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll  {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbeSpec._
  import Master._

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workloadString = "I love Akka"
      master ! Work(workloadString)

      // the interaction between the master actor and the slave actor
      slave.expectMsg(SlaveWork(workloadString, testActor))
      slave.reply(WorkCompleted(3, testActor))

      expectMsg(Report(3)) // testActor receives the Report(3)
    }

    "aggregate data correctly" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workloadString = "I love Akka"
      master ! Work(workloadString)
      master ! Work(workloadString)

      // in the meantime I don't have a slave actor
      slave.receiveWhile() {
        case SlaveWork(`workloadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
      }

      expectMsg(Report(3))
      expectMsg(Report(6))
    }
  }

}

object TestProbeSpec {
  // scenario

  /*
   word counting actor hierarchy master-slave

    send some word to the master
      - master sends the slave the piece of work
      - slave processes the work and replies to master
      - master aggregates the result
    master sends the total count to the original requester
   */

  object Master {
    case class Register(slave: ActorRef)
    case class Work(text: String)
    case class SlaveWork(text: String, originalRequester: ActorRef)
    case class WorkCompleted(count: Int, originalRequester: ActorRef)
    case class Report(totalCount: Int)
    case object RegistrationAck
  }
  class Master extends Actor {
    import Master._
    def receive: Receive = {
      case Register(slave) =>
        sender() ! RegistrationAck
        context.become(online(slave, 0))
      case _ => // ignore
    }
    private def online(slave: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => slave ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = totalWordCount + count
        originalRequester ! Report(newTotalWordCount)
        context.become(online(slave, newTotalWordCount))
    }
  }

  // class Slave extends Actor ...

}
