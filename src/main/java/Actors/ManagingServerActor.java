package Actors;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;


import java.io.Serializable;
import java.util.HashMap;

public class ManagingServerActor extends AbstractActor{

    static public Props props() {
        return Props.create(ManagingServerActor.class, () -> new ManagingServerActor());
    }

    //messages
    static public class Connect implements Serializable {
        public final String username;

        public Connect(String username) {
            this.username = username;
        }
    }

    static public class Disconnect implements Serializable {

        public Disconnect() {
        }
    }

    static public class GroupCreate implements Serializable{
        public final String groupname;
        public final boolean flag;

        public GroupCreate(String groupname,boolean flag) {
            this.groupname = groupname;
            this.flag = flag;
        }
    }

    static public class GroupLeave implements Serializable{
        public final String groupname;
        public final String username;

        public GroupLeave(String groupname, String username) {
            this.groupname = groupname;
            this.username = username;
        }
    }

    static public class CloseGroup implements  Serializable{
        public final String groupname;

        public  CloseGroup(String groupname){
            this.groupname = groupname;
        }
    }


    static public class GroupUserInvite implements Serializable{
        public final String groupname;
        public final String username;
        public final ActorRef whoInvites;

        public GroupUserInvite(String groupname, String username,ActorRef whoInvites){
            this.groupname = groupname;
            this.username = username;
            this.whoInvites = whoInvites;
        }
    }

    static public class GroupUserRemove implements Serializable{
        public final String groupname;
        public final String username;

        public GroupUserRemove(String groupname, String username){
            this.groupname = groupname;
            this.username = username;
        }
    }

    static public class GroupUserMute{
        public final String groupname;
        public final String username;
        public final int time;

        public GroupUserMute(String groupname, String username, int time){
            this.groupname = groupname;
            this.username = username;
            this.time = time;
        }
    }

    static public class GroupUserUnMute{
        public final String groupname;
        public final String username;

        public GroupUserUnMute(String groupname, String username){
            this.groupname = groupname;
            this.username = username;
        }
    }

    static public class GroupAdminAdd{
        public final String groupname;
        public final String username;

        public GroupAdminAdd(String groupname, String username){
            this.groupname = groupname;
            this.username = username;
        }
    }

    static public class GroupAdminRemove{
        public final String groupname;
        public final String username;

        public GroupAdminRemove(String groupname, String username){
            this.groupname = groupname;
            this.username = username;
        }
    }

    static public class setKey implements Serializable{
        public final ActorRef userRef;
        public final String username;
        public setKey(ActorRef useRef, String username){
            this.userRef = useRef;
            this.username = username;
        }
    }

    static public class AskUserPath implements Serializable{
        public final String user_name;
        public final ActorPath path;
        public final String message_type;
        public AskUserPath(String user_name, ActorPath path,String message_type){
            this.user_name = user_name;
            this.path = path;
            this.message_type = message_type;
        }
    }

    private HashMap<String, ActorRef> users;
    private HashMap<String, ActorRef> groups;
    private boolean up = false;

    public ManagingServerActor(){
        this.users = new HashMap<>();
        this.groups = new HashMap<>();
        this.up = true;
    }

    //TODO REMOVE
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);


    //    return receiveBuilder()
//        .match(WhoToGreet.class, wtg -> {
//        this.greeting = message + ", " + wtg.who;
//    })
//            .match(Greet.class, x -> {
//        //#greeter-send-message
//        printerActor.tell(new Greeting(greeting), getSelf());
//        //#greeter-send-message
//    })
//            .build();
//
    private ActorRef isExist(String type, String target, ActorRef toSend){
        HashMap<String, ActorRef> toSearch = (type.equals("user") ? users : groups);
        ActorRef ret;
        if((ret = toSearch.get(target)) == null)
            toSend.tell(new UserActor.UserOutput(target + " does not exist!"),ActorRef.noSender());
        return ret;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(setKey.class, sk -> {
                    users.put(sk.username, sk.userRef);
                    sk.userRef.tell(new UserActor.UserOutput(sk.username+" has connected successfully!"), ActorRef.noSender());
                })
                .match(Connect.class,c -> {
                    if(up){
                        if(users.get(c.username) != null)
                            getSender().tell(new UserManager.Ack(false,c.username),ActorRef.noSender());
                        else{
                            users.put(c.username,getSender());
                            System.out.println(c.username + " has connected successfully");
                            //TODO delete this is for testing
                            //tell the user that he has connected
                            getSender().tell(new UserManager.Ack(true,c.username),ActorRef.noSender());

                        }}
                    else
                        System.out.println("Server is offline");
                })
                .match(Disconnect.class, dis ->{
                    //TODO When we create groups make the user leave all groups "gracfully"
                    if(up){
                        //if actor is admin break the group **to_do**
                        String dis_name = getSender().path().name();
                        if(users.get(dis_name) == null)
                            System.out.println("No such user");
                        else {
                            users.remove(dis_name);
                            groups.forEach((k, v) -> {
                                v.tell(new GroupActor.GracefulyRemove(dis_name), getSelf());
                            });
                            getSender().tell("Disconnected",ActorRef.noSender());
                            //send user message he was removed! **to_do**
                        }
                    }
                    else
                        System.out.println("Try again later");
                })
                .match(AskUserPath.class, aup ->{
                    ActorRef ac = users.get(aup.user_name);
                    //if user exsits send its path to the asking user else send null!
                    if(ac != null)
                        getSender().tell(new AskUserPath(aup.user_name, ac.path(),aup.message_type),getSelf());
                    else
                        getSender().tell(new AskUserPath(aup.user_name, null,aup.message_type),getSelf());
                })
                .match(GroupCreate.class, gc -> {
                    String group_name = gc.groupname;
                    if( groups.get(group_name) == null){
                        //create group actor and add it to the list **to_do**
                        groups.put(group_name,context().actorOf(Props.create(GroupActor.class,group_name,getSender()),group_name));
                        getSender().tell(new GroupCreate(gc.groupname,true),ActorRef.noSender());
                    }
                    else{
                        System.out.println( gc.groupname + " already exists!");
                        //TODO catch this message
//                        getSender().tell("group already exists",getSender());
                        getSender().tell(new GroupCreate(gc.groupname,false),ActorRef.noSender());
                    }
                })
                .match(CloseGroup.class, cg -> {
                    groups.remove(cg.groupname).tell(PoisonPill.getInstance(), getSelf());;
                })
                .match(GroupLeave.class, gl ->{
                    String group_name = gl.groupname;
                    ActorRef group = groups.get(group_name);
                    //We have the group
                    if(group != null){
                        group.tell(gl,getSelf());
                    }
                    //group doesnt exist
                    else
                        getSender().tell(new UserActor.UserOutput(getSender().path().name() + " is not in "+group_name + "!"),ActorRef.noSender());
                })
                .match(GroupActor.SendGroupText.class, gst -> {
                    String group_name = gst.group;
                    ActorRef group = groups.get(group_name);
                    //the group exists
                    if(group != null){
                        //send also the sender so the group would know who am i!
                        group.tell(gst,getSender());
                    }
                    else
                        getSender().tell(gst,ActorRef.noSender());
                })
                .match(GroupActor.SendGroupFile.class, gsf -> {
                    String group_name = gsf.group;
                    ActorRef group = groups.get(group_name);
                    //the group exists
                    if(group != null){
                        //send also the sender so the group would know who am i!
                        group.tell(gsf,getSender());
                    }
                    else
                        getSender().tell(gsf,ActorRef.noSender());
                })
                .match(GroupUserInvite.class, gua -> {
                    ActorRef group = isExist("group", gua.groupname, getSender());
                    ActorRef user_inv = isExist("user", gua.username, getSender());
                    if(group != null && user_inv != null){
                        //sending the user invited so it would be easier to send a message to him
                        group.tell(gua,user_inv);
                    }
                   //add to group invited user **to_do**
                })
                .match(GroupUserRemove.class, gur -> {
                    //remove user from group **to_do**
                    ActorRef group = isExist("group", gur.groupname, getSender());
                    ActorRef removeUser = isExist("user", gur.username, getSender());
                    if(group != null && removeUser != null){
                        group.tell(gur,getSender());
                    }
                })
                .match(GroupActor.UserMute.class, um ->{
                    String groupname = um.group;
                    ActorRef group = isExist("group", groupname, getSender());
                    if(group != null){
                        um.flag = true;
                        group.tell(um, getSender());
                    }
                })
                .match(GroupActor.UserUnMute.class, uum -> {
                    String groupname = uum.groupname;
                    ActorRef group = isExist("group", groupname, getSender());
                    if(group != null){
                        group.tell(uum, getSender());
                    }
                })
                .match(GroupActor.AdminAdd.class, gad -> {
                    String groupname = gad.groupname;
                    ActorRef group = isExist("group", groupname, getSender());
                    if(group != null){
                        group.tell(gad, getSender());
                    }
                })
                .match(GroupActor.AdminRemove.class, gar -> {
                    String groupname = gar.groupname;
                    ActorRef group = isExist("group", groupname, getSender());
                    if(group != null){
                        group.tell(gar, getSender());
                    }
                }).build();
    }
}