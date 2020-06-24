package Actors;

import akka.actor.*;
import Actors.ManagingServerActor;
import akka.util.Timeout;

import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.Future;


import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;


public class UserActor extends AbstractActor {

    static public Props props(String username) {
        return Props.create(UserActor.class, () -> new UserActor(username));
    }

    static public class TextMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        String message;
        String name1;
        String sender;
        public TextMessage(String message, String name1, String sender) {
            this.message = message;
            this.sender = sender;
            this.name1 = name1;
        }
    }

    static public class FileMessage implements  Serializable{
        private static final long serialVersionUID = 1L;
        String file_name;
        byte[] fileContent;
        public FileMessage(String file_name, byte[] fileContent){
            this.file_name = file_name;
            this.fileContent = fileContent;
        }
    }

    static public class MuteMessage implements Serializable{
        private static final long serialVersionUID = 1L;
        String groupName;
        String message;
        public MuteMessage(String groupName, String message){
            this.groupName = groupName;
            this.message = message;
        }
    }

    static public class UserCommand {
        String[] command;
        public UserCommand(String[] command){
            this.command = command;
        }
    }

    static public class GroupCommand {
        String[] command;
        public  GroupCommand(String[] command){
            this.command = command;
        }
    }

    static public class UserOutput implements  Serializable{
        private static final long serialVersionUID = 1L;
        String output;
        public UserOutput(String output){
            this.output = output;
        }
    }


    /**
     * Actor selection, we should check what protcol we need to use
     */
    private ActorSelection selection = getContext().actorSelection("akka://ManagingServer@127.0.0.1:2552/user/ManagingServerActor");


    public final String username;
    private HashMap<String, LinkedList<String>>  messageQ;
    private HashMap<String, LinkedList<String>> fileQ;
    private static final Timeout TIMEOUT = new Timeout(Duration.create(1, TimeUnit.SECONDS));
    private ManagingServerActor.GroupUserInvite invite;
    private ActorRef inviting_group;


    //User actor builder
    public UserActor(String username){
        this.username = username;
        this.messageQ = new HashMap<>();
        this.fileQ = new HashMap<>();
    }

    /**
     * isManagingOn is function to check if the managing server is online
     * what it does is actually sending a message and waiting for the future to resolve
     * if it resolves the managing server is on else after timeout return false
     */
    public static boolean isManagingOn(ActorSelection sel){
        try{
            final Future<ActorRef> fut = sel.resolveOne(TIMEOUT);
            final ActorRef ref = Await.result(fut,TIMEOUT.duration());
            return true;
        }catch (Exception e){
            return false;
        }}

    /**
     * This functions concats the a string array to a string
     * @param splited
     * @return
     */
    private String build_message(String[] splited, int from){
        StringBuilder builder = new StringBuilder();
        for(int i = from; i< splited.length; i++){
            if(builder.length() > 0){
                builder.append(" ");
            }
            builder.append(splited[i]);
        }
        return builder.toString();
    }

    private void print(String firstName, String secondName, String msg){
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        Date date = new Date();
        System.out.println("[" + dateFormat.format(date) + "][" + firstName + "]" + "["+secondName +"]"+msg);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(String.class, s->{
                    //TODO fix this ugliness
                    if(invite != null){
                        if(s.compareToIgnoreCase("Yes") == 0){
                            inviting_group.tell(new GroupActor.inviteResult(true),getSelf());
                            invite = null;
                            inviting_group = null;
                        }
                        else if(s.compareToIgnoreCase("No") == 0){
                            inviting_group.tell(new GroupActor.inviteResult(false),getSelf());
                            invite = null;
                            inviting_group = null;
                        }
                        else {
                            System.out.println("Please answer yes or no, only");
                            return;
                        }
                    }
                    //if the server responds with in use we know the user is in use
//                    else if(s.equals("in use")){
//                        System.out.println(this.username + " is in use!");
//                    }
                    //if the server responds with ACK we know we connected successfully
                    else if(s.equals("ACK")){
                        System.out.println(this.username + " has connected successfully!");
                    }
                    else if(s.equals("Disconnected")){
                        System.out.println(this.username + " has been disconnected successfully!");
                    }
                    else
                        System.out.println("Wrong input try again!");
                })
                //send message to the server asking to create a new group.. wait for response if it was succesfull
                .match(GroupCommand.class, gc -> {
                    switch (gc.command[1]){

                        case "create":
                            selection.tell(new ManagingServerActor.GroupCreate(gc.command[2],false),getSelf());
                            break;
                        case "leave":
                            selection.tell(new ManagingServerActor.GroupLeave(gc.command[2], getSelf().path().name()),getSelf());
                            break;
                        case "send":
                            switch (gc.command[2]){
                                case "text":
                                    selection.tell(new GroupActor.SendGroupText(gc.command[3],build_message(gc.command,4),false),getSelf());
                                    break;
                                case "file":
                                    File file_to_send = new File(gc.command[4]);
                                    if(!file_to_send.exists()){
                                        System.out.println(file_to_send.getPath()+" does not exist!");
                                    }
                                    else{
                                        byte[] fileContent = Files.readAllBytes(file_to_send.toPath());
                                        selection.tell(new GroupActor.SendGroupFile(gc.command[3],file_to_send.getName(),fileContent,false),getSelf());
                                    }

                                    break;
                            }
                            break;
                        case "user":
                            if(gc.command.length >= 5)
                                switch (gc.command[2]){
                                    case "invite":
                                        selection.tell(new ManagingServerActor.GroupUserInvite(gc.command[3],gc.command[4],getSelf()),getSelf());
                                        break;
                                    case "remove":
                                        //TODO
                                        selection.tell(new ManagingServerActor.GroupUserRemove(gc.command[3],gc.command[4]),getSelf());
                                        break;
                                    case "mute":
                                        if(gc.command.length > 5)
                                            selection.tell(new GroupActor.UserMute(gc.command[3], gc.command[4], Integer.valueOf(gc.command[5]) ,getSelf()),getSelf());
                                        else
                                            System.out.println("wrong input, try again!");
                                        break;
                                    case "unmute":
                                        selection.tell(new GroupActor.UserUnMute(gc.command[3], gc.command[4], null), getSelf());
                                        break;
                                    default:
                                        System.out.println("wrong input, try again!");
                                }
                            break;
                        case "coadmin":
                            switch (gc.command[2]){
                                case "add":
                                    selection.tell(new GroupActor.AdminAdd(gc.command[4], gc.command[3]), getSelf());
                                    break;
                                case "remove":
                                    selection.tell(new GroupActor.AdminRemove(gc.command[4], gc.command[3]), getSelf());
                                    break;
                            }
                            break;
                        default:
                            System.out.println("wrong input, try again!");
                    }
                })
                //we get this message only from the group we have been inivted to!
                .match(ManagingServerActor.GroupUserInvite.class,gui ->{
                    System.out.println("You have been invited to " + gui.groupname + ", Accept?");
                    invite = gui;
                    inviting_group = getSender();

                })
                // wait for response to see if the message wasnt sent
                .match(GroupActor.SendGroupText.class, sgt -> {
                    if(!sgt.flag){
                        System.out.println(sgt.group + " does not exist!");
                    }
                })
                .match(GroupActor.SendGroupFile.class, sgt -> {
                    if(!sgt.flag){
                        System.out.println(sgt.group + " does not exist!");
                    }
                })
                .match(GroupActor.UserMute.class, um ->{
                    if(!um.flag){
                        System.out.println(um.group + " does not exist!");
                    }
                })
                /**
                 * The ManagingServer returns if we succeded in creating the group
                 */
                .match(ManagingServerActor.GroupCreate.class, gc ->{
                    if(gc.flag)
                        System.out.println(gc.groupname + " created successfully!");
                    else
                        System.out.println(gc.groupname + " already exists!");
                })
                .match(UserCommand.class, uc -> {
                    switch (uc.command[1]){
                        case "connect":
                            if(isManagingOn(selection))
                                selection.tell(new ManagingServerActor.Connect(uc.command[2]),getSelf());
                            else
                                System.out.println("server is offline!");
                            break;
                        case "disconnect":
                            if(isManagingOn(selection))
                                selection.tell(new ManagingServerActor.Disconnect(),getSelf());
                            else
                                System.out.println("server is offline! try again later!");
                            break;
                        /**
                         * We created two Hashmaps one for text message "messageQ" and one for file messages "fileQ"
                         * each position in the hashmap is a queue for the actors message
                         * when asking to send a new message
                         */
                        case "text":
                            if(messageQ.get(uc.command[2]) == null)
                                messageQ.put(uc.command[2],new LinkedList<>());
                            //TODO concat string from 3 till end to one message
                            messageQ.get(uc.command[2]).add(build_message(uc.command,3));
                            selection.tell(new ManagingServerActor.AskUserPath(uc.command[2],null,"text"),getSelf());
                            break;
                        case "file":
                            if(fileQ.get(uc.command[2]) == null)
                                fileQ.put(uc.command[2],new LinkedList<>());
                            fileQ.get(uc.command[2]).add(uc.command[3]);
                            selection.tell(new ManagingServerActor.AskUserPath(uc.command[2],null,"file"),getSelf());
                            break;
                        default:
                            System.out.println("No such command try again");
                    }
                })
                .match(ManagingServerActor.AskUserPath.class, aup ->{
                    switch(aup.message_type){
                        case "text":
                            if(aup.path != null)
                                getContext().actorSelection(aup.path).tell(new TextMessage(messageQ.get(aup.user_name).poll(), aup.user_name, username),getSelf());
                            else{
                                messageQ.remove(aup.user_name);
                                System.out.println(aup.user_name + " does not exist!");
                            }
                            break;
                        case "file":
                            if(aup.path != null) {
                                File file_to_send = new File(fileQ.get(aup.user_name).poll());
                                if(!file_to_send.exists()){
                                    System.out.println(file_to_send.getPath()+" does not exist!");
                                }
                                else{
                                    byte[] fileContent = Files.readAllBytes(file_to_send.toPath());
                                    getContext().actorSelection(aup.path).tell(new FileMessage(file_to_send.getName(),fileContent),getSelf());
                                }
                            }
                            else{
                                fileQ.remove(aup.user_name);
                                System.out.println(aup.user_name + " does not exist!");
                            }
                            break;
                    }
                })
                .match(TextMessage.class, tm -> {
                    print(tm.name1, tm.sender, tm.message);
                })
                .match(FileMessage.class, fm ->{
                    Files.write(new File(fm.file_name).toPath(), fm.fileContent);
                    print(this.username, getSender().path().name(), "File received: "+ new File(fm.file_name).getPath());
                })
                .match(MuteMessage.class, mm ->{
                    print(mm.groupName, getSender().path().name(), mm.message);
               })
                .match(UserOutput.class, uo->{
                    System.out.println(uo.output);
                })
                .build();
    }
}