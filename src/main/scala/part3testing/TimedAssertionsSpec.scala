package part3testing

import scala.util.Random
import scala.concurrent.duration._

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class TimedAssertionsSpec extends TestKit(
  ActorSystem("TimedAssertionsSpec", ConfigFactory.load().getConfig("specialTimedAssetionsConfig")))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TimedAssertionsSpec._
  import WorkerActor._

  "A worker actor" should {
    val worker = system.actorOf(Props[WorkerActor])

    "reply with the meaning of life in a timely manner" in {
      within(500 millis, 1 seconds) {
        worker ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with valid work at a reasonable cadence" in {
      within(1 second) {
        worker ! "workSequence"
        val results: Seq[Int] = receiveWhile[Int](max = 2 seconds, idle = 500 millis, messages = 10) {
          case WorkResult(result) => result
        }
        assert(results.sum > 5)
      }
    }

    "reply to a test probe in a timely manner" in {
      within(1 seconds) {
        val probe = TestProbe()
        probe.send(worker, "work")
        probe.expectMsg(WorkResult(42)) // timeout of 0.3 seconds
      }
    }

  }

}

object TimedAssertionsSpec {

  object WorkerActor {
    case class WorkResult(result: Int)
  }
  class WorkerActor extends Actor {
    import WorkerActor._
    def receive: Receive = {
      case "work" =>
        // long computation
        Thread.sleep(500)
        sender() ! WorkResult(42)

      case "workSequence" =>
        val r = new Random()
        for (_ <- 1 to 10) {
          Thread.sleep(r.nextInt(50))
          sender() ! WorkResult(1)
        }
    }
  }

}