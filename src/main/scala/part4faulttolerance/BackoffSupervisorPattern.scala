package part4faulttolerance

import java.io.File

import scala.concurrent.duration._
import scala.io.Source

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

object BackoffSupervisorPattern extends App {

  case object ReadFile
  class FileBasePersistentActor extends Actor with ActorLogging {
    var dataSource: Source = null

    override def preStart(): Unit = {
      log.info("Persistent actor starting")
    }

    override def postStop(): Unit = {
      log.warning("Persistent actor has stopped")
    }

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.warning("Persistent actor restarting")
    }

    def receive: Receive = {
      case ReadFile =>
        if (dataSource == null)
          dataSource = Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
        log.info("I've just read some IMPORTANT data: " + dataSource.getLines.toList)
    }
  }

  val system = ActorSystem("BackoffSupervisorDemo")
//  val simpleActor = system.actorOf(Props[FileBasePersistentActor], "simpleActor")
//  simpleActor ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      Props[FileBasePersistentActor],
      "simpleBackoffActor",
      3 seconds, // then 6s, 12s, 24s
      30 seconds,
      0.2
    )
  )

//  val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
//  simpleBackoffSupervisor ! ReadFile

  /*
    simpleSupervisor
      - child called simpleBackoffActor (props of type FileBasePersistentActor)
      - supervision strategy is the default one (restarting on everything)
        - first attempt after 3 seconds
        - next attempt is 2x the previous attempt
   */

  val stopSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      Props[FileBasePersistentActor],
      "stopBackoffActor",
      3 seconds,
      30 seconds,
      0.2
    ).withSupervisorStrategy(
      OneForOneStrategy() {
        case _ => Stop
      }
    )
  )

//  val stopSupervisor = system.actorOf(stopSupervisorProps, "stopSupervisor")
//  stopSupervisor ! ReadFile

  class EagerFileBasePersitentActor extends FileBasePersistentActor {
    override def preStart(): Unit = {
      log.info("Eager actor starting")
      dataSource = Source.fromFile(new File("src/main/resources/testfiles/important_data.txt"))
    }
  }

  // case ActorInitializationException => Stop

  val repeatedSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      Props[EagerFileBasePersitentActor],
      "eagerActor",
      1 second,
      30 seconds,
      0.1
    )
  )

  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "repeatedSupervisor")

  /*
    repeatedSupervisor
      - child eagerActor
        - will die on start with ActorInitializationException
        - trigger the supervision strategy in repe atedSupervisor => STOP eagerActor
      - backoff will kick in after 1 seconds, 2s, 4, 8, 16,
   */

}
