package part2actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

import com.typesafe.config.ConfigFactory

object IntroAkkaConfig extends App {

  class SimpleLoggingActor extends Actor with ActorLogging {
    def receive: Receive = {
      case message => log.info(message.toString)
    }
  }
  
  /**
    * 1 - inline configuration
    */
  val configString =
    """
      | akka {
      |   loglevel = "ERROR"
      | }
    """.stripMargin

  val config = ConfigFactory.parseString(configString)
  val system = ActorSystem("ConfigurationDemo", ConfigFactory.load(config))
  val logger = system.actorOf(Props[SimpleLoggingActor], "logger")
  logger ! "A message to remember"

  /**
    * 2 - file configuration
    */

  val defaultConfigFileDemo = ActorSystem("DefaultConfigFileDemo")
  val logger2 = defaultConfigFileDemo.actorOf(Props[SimpleLoggingActor], "logger2")
  logger2 ! "Remember me"

  /**
    * 3 - separate configuration in the same file
    */
  val specialConfig = ConfigFactory.load().getConfig("my-special-config")
  val specialConfigSystem = ActorSystem("SpecialConfigDemo", specialConfig)
  val logger3 = specialConfigSystem.actorOf(Props[SimpleLoggingActor], "logger3")
  logger3 ! "Remember me. I'm special"

  /**
    * 4 - separate configuration file
    */
  val separateConfig = ConfigFactory.load("secretFolder/secretConfiguration.conf")
  val separateConfigSystem = ActorSystem("SeparateConfigDemo", separateConfig)
  val logger4 = separateConfigSystem.actorOf(Props[SimpleLoggingActor], "logger4")
  logger4 ! "Remember me. I'm separate"

  /**
    * 5 - different file formats
    * JSON, Properties
    */
  val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
  val jsonConfigSystem = ActorSystem("JsonConfigDemo", jsonConfig)
  val logger5 = jsonConfigSystem.actorOf(Props[SimpleLoggingActor], "logger5")
  logger5 ! "Remember me. I'm a json config"

  val propsConfig = ConfigFactory.load("props/propsConfig.properties")
  val propsConfigSystem = ActorSystem("PropsConfigDemo", propsConfig)
  val logger6 = propsConfigSystem.actorOf(Props[SimpleLoggingActor], "logger6")
  logger6 ! "Remember me. I'm a props config"

}
