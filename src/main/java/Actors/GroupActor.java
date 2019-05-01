package Actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;


import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;

public class GroupActor extends AbstractActor {

    static public Props props(String groupname, ActorRef admin) {
        return Props.create(GroupActor.class, () -> new GroupActor(groupname, admin));
    }

    //messages types
    static public class SendGroupText implements Serializable {
        public final String message;
        public final String group;
        public final boolean flag;

        public SendGroupText(String group,String message, boolean flag) {
            this.group = group;
            this.message = message;
            this.flag = flag;
        }
    }
    static public class SendGroupFile implements Serializable {
        public final byte[] file;
        public final String file_name;
        public final String group;
        public final boolean flag;

        public SendGroupFile(String group,String file_name, byte[] file, boolean flag) {
            this.group = group;
            this.file_name = file_name;
            this.file = file;
            this.flag = flag;
        }
    }



    static public class inviteResult implements Serializable{
        public final boolean result;

        public inviteResult(boolean result){
            this.result = result;
        }
    }

    static public class UserRemove{
        public final String username;

        public UserRemove(String username){
            this.username = username;
        }
    }


    static public class UserAdd{
        public final String username;
        public final ActorRef user;

        public UserAdd(String username, ActorRef user){
            this.username = username;
            this.user = user;
        }
    }

    static public class UserMute implements Serializable{
        public final String username;
        public final int time;
        public final ActorRef sender;
        public final String group;
        boolean flag;

        public UserMute(String group, String username, int time, ActorRef sender){
            this.group = group;
            this.username = username;
            this.time = time;
            this.sender = sender;
        }
    }

    static public class UserUnMute implements Serializable{
        public final String username;
        public final String message;
        public final String groupname;

        public UserUnMute(String groupname, String username, String message){
            this.username = username;
            this.groupname = groupname;
            this.message = message;

        }
    }

    static public class AdminAdd implements Serializable{
        public final String username;
        public final String groupname;

        public AdminAdd(String username, String groupname){
            this.username = username;
            this.groupname = groupname;
        }
    }

    static public class AdminRemove implements  Serializable{
        public final String username;
        public final String groupname;

        public AdminRemove(String username, String groupname){
            this.username = username;
            this.groupname = groupname;
        }
    }

    static public class GracefulyRemove implements Serializable{
        public final String username;

        public GracefulyRemove(String username){
            this.username = username;
        }
    }

    public final String groupname;
    private HashMap<String, ActorRef> users;
    private HashMap<String, ActorRef> admins;
    private HashMap<String, ActorRef> mutes;
    private ActorRef admin;

    public GroupActor(String groupname, ActorRef admin){
        this.groupname = groupname;
        this.users = new HashMap<>();
        this.admins = new HashMap<>();
        this.admin = admin;
        this.mutes = new HashMap<>();
        users.put(admin.path().name(), admin);
        admins.put(admin.path().name(), admin);

    }

    private boolean isExist(String username, ActorRef sender){
        if(users.get(username) == null){
            sender.tell(new UserActor.UserOutput(username + " does not exist!"), ActorRef.noSender());
            return false;
        }
        return true;
    }

    private boolean isAdmin(String username){
        if(admins.get(username) == null){
            users.get(username).tell(new UserActor.UserOutput("You are neither an admin nor a co-admin of " + groupname),ActorRef.noSender());
            return false;
        }
        return true;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SendGroupText.class, sgt->{
                    if(mutes.containsValue(getSender()))
                        return;
                    if(!(users.containsValue(getSender()))){
                        getSender().tell(new UserActor.UserOutput("you are not member in " +groupname+"!"), getSelf());
                        return;
                    }
                    users.forEach((k,v) -> {
                        v.tell(new UserActor.TextMessage(sgt.message, groupname,getSender().path().name()),getSelf());
                    });
                })
                .match(SendGroupFile.class, sgf->{
                    if(mutes.containsValue(getSender()))
                        return;
                    if(!(users.containsValue(getSender()))){
                        getSender().tell(new UserActor.UserOutput("you are not member in " +groupname+"!"), getSelf());
                        return;
                    }
                    for(ActorRef ref : users.values()){
                        ref.tell(new UserActor.FileMessage(sgf.file_name,sgf.file),getSelf());
                    }
                })
                .match(ManagingServerActor.GroupLeave.class, gl->{
                    String username = gl.username;
                    //the user asking to leave is in the group
                    if(users.get(username) != null){
                        //the user asking to leave is not admin or co-admin
                        boolean is_admin = (username.equals(admin.path().name()));
                        //co-admin
                        if(admins.get(username)!= null && !is_admin){
                            //co admin
                            for(ActorRef ref : users.values())
                                ref.tell(new UserActor.UserOutput(username + " is removed from co-admin list in" + groupname),ActorRef.noSender());
                            admins.remove(username);

                        }
                        users.forEach((k,v) -> {
                            v.tell(new UserActor.UserOutput(username + " has left " + groupname + "!"), ActorRef.noSender());
                            //if the admin closed
                            if(is_admin)
                                v.tell(new UserActor.UserOutput(groupname + " admin has closed " + groupname + "!"), ActorRef.noSender());
                        });
                        if(is_admin){
                            //so no more messages will be sent to the group
                            getSender().tell(new ManagingServerActor.CloseGroup(groupname),ActorRef.noSender());}

                    }
                    else
                    {
                        getSender().tell(new UserActor.UserOutput(username + " is not in "+groupname+"!"),ActorRef.noSender());
                    }
                })
                .match(GracefulyRemove.class, gr ->{
                    if(users.get(gr.username) == null) //user not in this group
                        return;
                    boolean is_admin = (gr.username.equals(admin.path().name()));
                    if(is_admin){//dismanled group
                        getSender().tell(new ManagingServerActor.CloseGroup(groupname),ActorRef.noSender());
                    }
                    else
                        users.remove(gr.username);
                    admins.remove(gr.username);
                    mutes.remove(gr.username);
                })
                .match(ManagingServerActor.GroupUserInvite.class, gui -> {
                    if(isAdmin(gui.whoInvites.path().name()))
                        getSender().tell(gui,getSelf());
                })
                .match(inviteResult.class, ir->{
                    String uname = getSender().path().name();
                    if(ir.result){
                        if(users.get(uname) == null){
                            users.put(uname,getSender());
                            getSender().tell(new UserActor.UserOutput("Welcome to " + groupname + "!"),ActorRef.noSender());
                        }
                    }

                })
                .match(ManagingServerActor.GroupUserRemove.class, gur->{
                    ActorRef userToRemove = users.get(gur.username);
                    if(isAdmin(getSender().path().name())){
                        if(userToRemove != null && userToRemove != admin){
                            users.remove(gur.username);
                            //if the admin removes a co-admin
                            admins.remove(gur.username);
                            userToRemove.tell(new UserActor.UserOutput("You have been romoved from group "+groupname+"by "+getSender().path().name()),ActorRef.noSender());
                        }
                        else
                            getSender().tell(new UserActor.UserOutput("No user with name "+gur.username+" in the group!"),ActorRef.noSender());
                    }
                })
                .match(UserMute.class, um->{
                    ActorRef userTomute;
                    if(mutes.containsValue(getSender()))
                        return;
                    if((isExist(um.username, um.sender))){
                        if(isAdmin(um.sender.path().name())){
                            userTomute = users.get(um.username);
                            mutes.put(um.username, userTomute);
                            userTomute.tell(new UserActor.MuteMessage(groupname, "You have been muted for " + um.time +" in "+groupname+" by "+um.sender.path().name()), um.sender);
                            context().system().scheduler()
                                    .scheduleOnce(Duration.ofSeconds(um.time), getSelf(), new GroupActor.UserUnMute(groupname, um.username, "You have been unmuted! Muting time is up!"), context().system().dispatcher(), getSender());
                        }
                    }
                })
                .match(UserUnMute.class, uum->{
                    if(mutes.containsValue(getSender()))
                        return;
                    ActorRef userToUnMute;
                    if(isExist(uum.username, getSender())){
                        if(isAdmin(getSender().path().name())){
                            if((userToUnMute = mutes.remove(uum.username)) == null)
                                getSender().tell(new UserActor.UserOutput(uum.username + " is not muted!"), getSelf());
                            else if(uum.message == null)//TODO: to ask him if this is the real message he want to get
                                userToUnMute.tell(new UserActor.MuteMessage(groupname, "You have been unmuted in "+groupname+" by "+ getSender().path().name()), getSender());
                            else
                                userToUnMute.tell(new UserActor.UserOutput(uum.message), getSelf());
                        }
                    }
                })
                .match(AdminAdd.class, aa->{
                    if(admin != getSender() || mutes.containsValue(getSender()))
                        return;
                    ActorRef newAdmin;
                    if(isExist(aa.username, getSender())){
                        if(isAdmin(getSender().path().name())){
                            newAdmin =users.get(aa.username);
                            admins.put(aa.username, newAdmin);
                            newAdmin.tell(new UserActor.UserOutput("You have been promoted to co-admin in " + groupname +"!"), ActorRef.noSender());
                        }
                    }
                })
                .match(AdminRemove.class, ar->{
                    if(mutes.containsValue(getSender()))
                        return;
                    if(isExist(ar.username, getSender())){
                        if(isAdmin(getSender().path().name())){
                            admins.remove(ar.username);
                            users.get(ar.username).tell(new UserActor.UserOutput("You have been demoted to user in " + groupname +"!"), ActorRef.noSender());
                        }
                    }
                })
                .build();
    }


}