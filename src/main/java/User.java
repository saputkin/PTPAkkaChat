
import Actors.UserManager;
import akka.actor.*;
import Actors.UserActor;
import com.typesafe.config.ConfigFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class User {

    static ActorRef user_ref = null;



    public static void main(String[] args){
        //Create Environment
        ActorSystem system = ActorSystem.create("UserSystem", ConfigFactory.load("application.conf").getConfig("UserConf"));
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
         ActorRef user_ref = system.actorOf((Props.create(UserManager.class)),"UserManager");
        String user_input = null;
        String[] splited_input = null;
        while(true){
            try{
            user_input = reader.readLine();}
            catch (IOException e){
                e.printStackTrace();
            }
            splited_input = user_input.split(" ");
            switch (splited_input[0]){
                case "/user":
                    user_ref.tell(new UserActor.UserCommand(splited_input),ActorRef.noSender());
                    break;
                case "/group":
                    user_ref.tell(new UserActor.GroupCommand(splited_input),ActorRef.noSender());
                    break;
                default:
                    user_ref.tell(user_input,ActorRef.noSender());
            }

        }
        //Create an actor
//        ActorRef client = system.actorOf(Props.create(UserActor.class,"namename"),"userActorTest");
//        client.tell("hello",client);


    }

}