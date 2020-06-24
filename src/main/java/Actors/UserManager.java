package Actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;

import java.io.Serializable;

public class UserManager extends AbstractActor {

    static public Props props() {
        return Props.create(UserManager.class, () -> new UserManager());
    }

    static public class Ack implements Serializable {
        boolean flag;
        String name;
        public  Ack(boolean flag, String name){
            this.flag = flag;
            this.name = name;
        }
    }

    private ActorSelection selection = getContext().actorSelection("akka://ManagingServer@127.0.0.1:2552/user/ManagingServerActor");
    ActorRef user;

    public UserManager(){

    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(String.class, s->{
                    if(user != null){
                        user.tell(s,ActorRef.noSender());
                    }
                    else
                        System.out.println("First connect to the server");
                })
                .match(Ack.class, ack ->{
                    if(ack.flag){
                        user = getContext().actorOf(Props.create(UserActor.class,ack.name),ack.name);
                        selection.tell(new ManagingServerActor.setKey(user, ack.name), user);
                    }
                    else {
                        System.out.println(ack.name + " is in use!");
                    }
                })
                .match(UserActor.UserCommand.class,uc ->{
                    if(uc.command[1].equals("connect") && user == null){
                        if(UserActor.isManagingOn(selection)){
                            selection.tell(new ManagingServerActor.Connect(uc.command[2]),getSelf());
                        }
                        else
                            System.out.println("server is offline!");
                    }
                    else if(user == null ){
                        System.out.println("First connect to the server");
                    }
                    else{
                        user.tell(uc, getSender());
                    }
                })
                .match(UserActor.GroupCommand.class, gc->{
                    if(user != null){
                        user.tell(gc,ActorRef.noSender());
                    }
                    else{
                        System.out.println("First connect to the server");
                    }
                })
                .match(String[].class, s->{
                    if(user != null){
                        user.tell(s,ActorRef.noSender());
                    }
                    else
                        System.out.println("First connect to the server");
                })
                .build();
    }



}