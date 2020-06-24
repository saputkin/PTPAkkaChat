
import akka.actor.Props;
import akka.actor.ActorSystem;
import Actors.ManagingServerActor;
import com.typesafe.config.ConfigFactory;

public class ManagingServer {

    public static void main(String[] args){
        //Create Environment
        ActorSystem system = ActorSystem.create("ManagingServer", ConfigFactory.load("application.conf").getConfig("ManagingServerConf"));

        //Create an actor
        system.actorOf(Props.create(ManagingServerActor.class),"ManagingServerActor");

    }

}
