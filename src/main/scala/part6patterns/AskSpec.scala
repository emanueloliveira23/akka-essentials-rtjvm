package part6patterns

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout

// step 1 - import ask pattern
import akka.pattern.ask
import akka.pattern.pipe

import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class AskSpec extends TestKit(ActorSystem("AskSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll  {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import AskSpec._
  import AuthManager._

  "An Authenticator" should {
    authenticatorTestSuite(Props[AuthManager])
  }

  "A piped Authenticator" should {
    authenticatorTestSuite(Props[PipedAuthManager])
  }

  def authenticatorTestSuite(props: Props) = {
    "fail to authenticate a non-registered user" in {
      val authManager = system.actorOf(props)
      authManager ! Authenticate("daniel", "rtvjm")
      expectMsg(AuthFailure(AuthFailureNotFound))
    }
    "fail to authenticate if invalid password" in  {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("daniel", "rtvjm")
      authManager ! Authenticate("daniel", "iloveakka")
      expectMsg(AuthFailure(AuthFailurePasswordIncorrect))
    }
    // AND THE SYSTEM ERROR TEST CASE?
    "success authenticate" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("daniel", "rtvjm")
      authManager ! Authenticate("daniel", "rtvjm")
      expectMsg(AuthSuccess)
    }
  }

}

object AskSpec {

  // this code is somewhere else in you app
  case class Read(key: String)
  case class Write(key: String, value: String)
  class KVActor extends Actor with ActorLogging {
    def receive: Receive = online(Map.empty)
    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Trying to read the value at the key $key")
        sender() ! kv.get(key) // Option[String]
      case Write(key, value) =>
        log.info(s"Writing the value $value for the key $key")
        context.become(online(kv + (key -> value)))
    }
  }

  // user authenticator actor
  case class RegisterUser(username: String, password: String)
  case class Authenticate(username: String, password: String)
  case class AuthFailure(message: String)
  case object AuthSuccess
  object AuthManager {
    val AuthFailureNotFound = "username not found"
    val AuthFailurePasswordIncorrect = "password incorrect"
    val AuthFailureSystemError = "system error"
    val AuthFailureUnexpectedBehavior = "unexpected behavior"
  }
  class AuthManager extends Actor with ActorLogging {
    import AuthManager._
    // step 2 - logistics
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val ec: ExecutionContext = context.dispatcher
    protected val authDb = context.actorOf(Props[KVActor])
    def receive: Receive = {
      case RegisterUser(username, password) => authDb ! Write(username, password)
      case Authenticate(username, password) => handleAuthentication(username, password)

    }
    protected def handleAuthentication(username: String, password: String): Unit = {
      val originalSender = sender()
      // step 3 - ask the actor
      val future = authDb ? Read(username)
      // step 4 - handle the future for e.g. with onComplete
      future.onComplete {
        // step 5 most important
        // NEVER CALL METHODS ON THE ACTOR INSTANCE OR ACCESS MUTABLE STATE IN ONCOMPLETE.
        // avoid closing over the actor instance or mutable state
        case Success(None) => originalSender ! AuthFailure(AuthFailureNotFound)
        case Success(Some(dbPassword)) =>
          if (dbPassword == password) originalSender ! AuthSuccess
          else originalSender ! AuthFailure(AuthFailurePasswordIncorrect)
        case Failure(_) => originalSender ! AuthFailure(AuthFailureSystemError)
        case _ => originalSender ! AuthFailure(AuthFailureUnexpectedBehavior)
      }
    }
  }

  class PipedAuthManager extends AuthManager {
    import AuthManager._
    override protected def handleAuthentication(username: String, password: String): Unit = {
      // step 3 - ask the actor
      val future = authDb ? Read(username) // Future[Any]
      // step 4 - process the future until you get responses you will send back
      val passwordFuture = future.mapTo[Option[String]] // Future[Option[String]]
      val responseFuture = passwordFuture.map {
        case None => AuthFailure(AuthFailureNotFound)
        case Some(dbPassword) =>
          if (dbPassword == password) AuthSuccess
          else AuthFailure(AuthFailurePasswordIncorrect)
      }.recover {
        case _ => AuthFailure(AuthFailureSystemError)
      }

      // Future[AuthSuccess/AuthFailure] -> Future[Any] - it will be completed with the response I will send back
      // AND THE SYSTEM ERROR?
      // step 5 - pipe the resulting future to the actor you want to send the result to
      /*
        When the future completes, send the response to the actor ref in the arg list
       */
      responseFuture.pipeTo(sender())
    }
  }

}
