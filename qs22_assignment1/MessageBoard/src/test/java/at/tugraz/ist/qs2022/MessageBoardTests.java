package at.tugraz.ist.qs2022;

import at.tugraz.ist.qs2022.actorsystem.DeterministicChannel;
import at.tugraz.ist.qs2022.actorsystem.Message;
import at.tugraz.ist.qs2022.actorsystem.SimulatedActor;
import at.tugraz.ist.qs2022.actorsystem.SimulatedActorSystem;
import at.tugraz.ist.qs2022.messageboard.*;
import at.tugraz.ist.qs2022.messageboard.clientmessages.*;
import at.tugraz.ist.qs2022.messageboard.clientmessages.Reaction.Emoji;
import at.tugraz.ist.qs2022.messageboard.dispatchermessages.Stop;
import at.tugraz.ist.qs2022.messageboard.dispatchermessages.StopAck;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.*;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.DeleteLikeOrDislike.Type;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.AddDislike;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.AddLike;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.AddReaction;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.AddReport;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.DeleteLikeOrDislike;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.MessageStoreMessage;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.RetrieveFromStore;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.SearchInStore;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.UpdateMessageStore;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.DeleteLikeOrDislike.Type;
import at.tugraz.ist.qs2022.messageboard.Worker;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

//commit test mpuni

/**
 * Simple actor, which can be used in tests, e.g. to check if the correct messages are sent by workers.
 * This actor can be sent to workers as client.
 */
class TestClient extends SimulatedActor {

    /**
     * Messages received by this actor.
     */
    final Queue<Message> receivedMessages;

    TestClient() {
        receivedMessages = new LinkedList<>();
    }

    /**
     * does not implement any logic, only saves the received messages
     *
     * @param message Non-null message received
     */
    @Override
    public void receive(Message message) {
        receivedMessages.add(message);
    }
}

public class MessageBoardTests {

    @Test
    public void testSameMessageUpdate() throws UnknownClientException, UnknownMessageException {
        // testing only the acks
        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);

        // send request and run system until a response is received
        // communication id is chosen by clients
        dispatcher.tell(new InitCommunication(client, 10));
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        // end the communication
        worker.tell(new FinishCommunication(10));
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message finAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(FinishAck.class, finAckMessage.getClass());
        FinishAck finAck = (FinishAck) finAckMessage;

        Assert.assertEquals(Long.valueOf(10), finAck.communicationId);
        dispatcher.tell(new Like("name", 10, 10));
        dispatcher.tick();
        dispatcher.tell(new Stop());
        
        dispatcher.tell(new InitCommunication(client, 10));
        
        
        //system.runFor(2);

        // TODO: run system until workers and dispatcher are stopped

        while (system.getActors().size() > 1)
            system.runFor(1);

    }

    // TODO: Implement test cases
    @Test
    public void testSimulatedActorSystem() throws UnknownClientException, UnknownMessageException {
        List<SimulatedActor> actors = new ArrayList<>();
        SimulatedActorSystem system = new SimulatedActorSystem();
        TestClient client = new TestClient();
        system.spawn(client);
        actors.add(client);
        //test getActors()
        Assert.assertEquals(system.getActors(), actors);

        //tests runFor() and getCurrentTime()
        int no_ticks = 10;
        system.runFor(no_ticks);
        Assert.assertEquals(system.getCurrentTime(), no_ticks);

        //test runUntil()
        
        int end_time = 500;
        Assert.assertTrue(system.getCurrentTime() == 10);
        system.runUntil(end_time);
        Assert.assertTrue(end_time + 1 == system.getCurrentTime());

        //tests stop()
        system.stop(client);
        actors.remove(client);
        Assert.assertEquals(system.getActors(), actors);

        //TESTs FOR SimulatedActor
        for(int i = 1; i <= 10; i++){
            system.spawn(client);
            Assert.assertEquals(client.getId(), i);
        }

        system.spawn(client);
        Assert.assertTrue(system.getCurrentTime() == client.getTimeSinceSystemStart());

        
        //Assert.assertTrue(client.getMessageLog().isEmpty());
        
    }


    @Test
    public void TestUserMessageAndSimulatedActorAndStopAck() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        UserMessage usermessage = new UserMessage("author", "message");
        Assert.assertEquals("author", usermessage.getAuthor()); 
        Assert.assertEquals("message", usermessage.getMessage());
        usermessage.setPoints(10);
        Assert.assertEquals(10, usermessage.getPoints());
        usermessage.setMessageId(10);
        Assert.assertEquals(10, usermessage.getMessageId());

        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        InitAck initAck = (InitAck) initAckMessage;
        SimulatedActor worker = initAck.worker;
        worker.setId(5);
        Assert.assertEquals(5, worker.getId());

        Publish publish = new Publish(usermessage, 10);
        Like like = new Like("client", 10, usermessage.getMessageId());
        Dislike dislike = new Dislike("client", 10, usermessage.getMessageId());

        worker.tell(publish); 
        worker.tell(like);
        worker.tell(dislike);

        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        for(int i = 0; i < 1000; i++)
            worker.tick();

        //could be more meaningful?
        Assert.assertEquals(0, usermessage.getLikes().size());

        //could be more meaningful?
        Assert.assertEquals(0, usermessage.getDislikes().size());

        //could be more meaningful?
        Assert.assertEquals(0, usermessage.getReactions().size());

        
        if (worker.getMessageLog().size() <= 3)
            assert(false);
        
        //would be meaningful if I'd get messages to be processed
        //Assert.assertEquals(11, usermessage.getPoints());

        Assert.assertEquals("author:message liked by : disliked by :; Points: 10", usermessage.toString());


        ///////////////////////////
        StopAck stopAck = new StopAck(worker);
        Assert.assertEquals(2, stopAck.getDuration());

        SearchInStore sis = new SearchInStore("author", 10);
        Assert.assertEquals(10, sis.communicationId);
        Assert.assertEquals("author", sis.searchText);

        RetrieveFromStore rfs = new RetrieveFromStore("author", 10);
        Assert.assertEquals(10, rfs.communicationId);
        Assert.assertEquals("author", rfs.author);

        MessageStoreMessage msgStoreMsg =  new AddLike("client", 10, 10);
        Assert.assertEquals(1, msgStoreMsg.getDuration());

        UpdateMessageStore updMsgStore = new UpdateMessageStore(usermessage, 10);
        Assert.assertEquals(usermessage, updMsgStore.message);
        Assert.assertEquals(10, updMsgStore.communicationId);

        AddReport addReport = new AddReport("client", 10, "client");
        Assert.assertEquals("client", addReport.clientName);
        Assert.assertEquals(10, addReport.communicationId);
        Assert.assertEquals("client", addReport.reportedClientName);

        DeleteLikeOrDislike deleteLikeOrDislike = new DeleteLikeOrDislike("client", 10, 10, Type.DISLIKE);
        Assert.assertEquals("client", deleteLikeOrDislike.clientName);
        Assert.assertEquals(10, deleteLikeOrDislike.messageId);
        Assert.assertEquals(10, deleteLikeOrDislike.communicationId);
        Assert.assertEquals(Type.DISLIKE, deleteLikeOrDislike.typeToDelete);

        DeleteLikeOrDislike deleteLikeOrDislike1 = new DeleteLikeOrDislike("client", 10, 10, Type.LIKE);
        Assert.assertEquals("client", deleteLikeOrDislike1.clientName);
        Assert.assertEquals(10, deleteLikeOrDislike1.messageId);
        Assert.assertEquals(10, deleteLikeOrDislike1.communicationId);
        Assert.assertEquals(Type.LIKE, deleteLikeOrDislike1.typeToDelete);

        AddReaction reaction = new AddReaction("client", 10, 10, Reaction.Emoji.COOL);
        Assert.assertEquals("client", reaction.clientName);
        Assert.assertEquals(10, reaction.messageId);
        Assert.assertEquals(10, reaction.communicationId);
        Assert.assertEquals(Reaction.Emoji.COOL, reaction.reaction);

        List<Message> expected_message_log = Arrays.asList(initComm, publish, like, dislike);
        Assert.assertEquals(expected_message_log, worker.getMessageLog());

        

        /*
        if (worker.getMessageLog().size() <= 3)
            assert(false);
        */

        //would be meaningful if I'd get messages to be processed
        //Assert.assertEquals(11, usermessage.getPoints());

        //could be more meaningful?
        //Assert.assertEquals(0, usermessage.getLikes().size());

        //could be more meaningful?
        //Assert.assertEquals(0, usermessage.getDislikes().size());

        //could be more meaningful?
        //Assert.assertEquals(0, usermessage.getReactions().size());

        //Assert.assertEquals("author:message liked by : disliked by :; Points: 10", usermessage.toString());

    }

    @Test
    public void TestStop() throws UnknownClientException, UnknownMessageException {
        
        Stop stop = new Stop();
        Assert.assertEquals(2, stop.getDuration());
    }

    @Test
    public void TestUnknownClientExceptionAndUnknownMessageException() throws UnknownClientException, UnknownMessageException {
        
        UnknownClientException exception = new UnknownClientException("message");
        Assert.assertEquals("message", exception.getMessage());

        UnknownMessageException exceptionMessage = new UnknownMessageException("message");
        Assert.assertEquals("message", exceptionMessage.getMessage());
    }


    @Test
    public void testWorker() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 3);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);

        // send request and run system until a response is received
        // communication id is chosen by clients
        dispatcher.tell(new InitCommunication(client, 10));
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        UserMessage usermessage = new UserMessage("author", "message");
        Assert.assertEquals("author", usermessage.getAuthor());
        Assert.assertEquals("message", usermessage.getMessage());
        usermessage.setPoints(10);
        Assert.assertEquals(10, usermessage.getPoints());
        //usermessage.setMessageId(12);

        Publish pubm = new Publish(usermessage, 10);
        client.tell(pubm);
        
        System.out.print(client.receivedMessages);
        

        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        System.out.print(client.receivedMessages);
        

        worker.tell(new UpdateMessageStore(usermessage, 10));
        system.tick();
        worker.tell(new Like("client", 10, usermessage.getMessageId()));
        system.tick();
        worker.tell(new Dislike("client", 10, usermessage.getMessageId()));
        system.tick();
        worker.tell(new RetrieveFromStore("client", 10));
        system.tick();
        worker.tell(new RetrieveMessages("client", 10));
        system.tick();
        worker.tell(new Report("client", 10, "client"));
        worker.tell(new Report("client42", 10, "client"));
        system.tick();
        worker.tell(new UpdateMessageStore(usermessage, 10));
        system.tick();
        worker.tell(new DeleteLikeOrDislike("client", 10, usermessage.getMessageId(), Type.LIKE));
//          
        worker.tell(new SearchMessages("message", 10));
        system.tick();
        worker.tell(new SearchMessages("hi", 10));
        //Assert.assertThrows(UnknownClientException.class, ()->{
        //    worker.tell(new SearchMessages("message", 11));
        //}); 
        //system.tick();
        //worker.tell(new Stop());
        //system.tick();
        //worker.tell(new Report("client", 10, "client"));
        //system.tick();
        //Assert.assertThrows(UnknownClientException.class, ()->{
        //    worker.tell(new Report("client", 42, "client"));
        //});
        
        system.tick();

        worker.tell(new DeleteLikeOrDislike("client", 10, usermessage.getMessageId(), Type.LIKE));
        system.tick();

        //worker.tell(new InitCommunication(worker, 10));
        //system.tick();

       // Assert.assertThrows(UnknownClientException.class, ()->{
           // worker.tell(new InitCommunication(worker, 10));
           // system.tick();
           // while (client.receivedMessages.size() == 0)
               // system.runFor(1);
            //worker.tell(new Stop());
            //while (system.getActors().size() > 1)
                //system.runFor(1);
       // }); 
        

        
        worker.tell(new Like("client", 10, usermessage.getMessageId()));

        System.out.print(usermessage.getPoints());
        

        while (client.receivedMessages.size() == 0)
            system.runFor(1);
            
        System.out.print(client.receivedMessages);

        //System.out.print(usermessage.getPoints());


        // end the communication
        //worker.receive(null);
        //worker.tick();
        worker.tell(new FinishCommunication(10));
        system.tick();
       // worker.tell(new FinishCommunication(10));
        system.tick();

        while (client.receivedMessages.size() == 0)
            system.runFor(1);



        //worker.tell(new FinishCommunication(10));
        //worker.tell(new Like("client", 10, usermessage.getMessageId()));
        //Message finAckMessage = client.receivedMessages.remove();
        //Assert.assertEquals(FinishAck.class, finAckMessage.getClass());
        //FinishAck finAck = (FinishAck) finAckMessage;

        //Assert.assertEquals(Long.valueOf(10), finAck.communicationId);
        dispatcher.tell(new Stop());

        // TODO: run system until workers and dispatcher are stopped

        while (system.getActors().size() > 1)
           system.runFor(1);
    }
    


    @Test
    public void testWorkerNULL() throws UnknownClientException, UnknownMessageException {
        // testing only the acks
        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);

        // send request and run system until a response is received
        // communication id is chosen by clients
        dispatcher.tell(new InitCommunication(client, 10));
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;
        Assert.assertThrows(UnknownMessageException.class, ()->{
            worker.receive(null);
        });
        system.tick();

        // end the communication
        worker.tell(new FinishCommunication(10));
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message finAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(FinishAck.class, finAckMessage.getClass());
        FinishAck finAck = (FinishAck) finAckMessage;

        Assert.assertEquals(Long.valueOf(10), finAck.communicationId);
        dispatcher.tick();
        dispatcher.tell(new Stop());
        
        //system.runFor(2);

        // TODO: run system until workers and dispatcher are stopped

        while (system.getActors().size() > 1)
            system.runFor(1);

    }

    @Test
    public void testClientMessages() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        //MessageStore msg_store = new MessageStore();

        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        while (client.receivedMessages.size() == 0)
            system.runFor(1);


        Message initAckMessage = client.receivedMessages.remove();
        InitAck initAck = (InitAck) initAckMessage;
        SimulatedActor worker = initAck.worker;
        
       
        UserMessage usermessage = new UserMessage("author", "message");
        Reaction reaction = new Reaction("client", 10, usermessage.getMessageId(), Emoji.SMILEY);
        Assert.assertEquals(reaction.getDuration(), 1);

        Report report = new Report("client", 10, "reportedClient");
        Assert.assertEquals(report.getDuration(), 1);

        RetrieveMessages r_msg = new RetrieveMessages("author", 10);
        Assert.assertEquals(r_msg.getDuration(), 3);

        SearchMessages s_msg = new SearchMessages("bingobongo", 10);
        Assert.assertEquals(s_msg.getDuration(), 3);

        List<UserMessage> messages = new ArrayList<UserMessage>();
        messages.add(usermessage);
        FoundMessages f_msg = new FoundMessages(messages, 10);
        Assert.assertEquals(f_msg.getDuration(), 1);

        system.tick();
        worker.tell(reaction);
        system.tick();
        worker.tell(report);
        system.tick();
        worker.tell(r_msg);
        system.tick();
        worker.tell(s_msg);
        system.tick();
        worker.tell(f_msg);
        system.tick();
     
        
        
        UserBanned user = new UserBanned(10);
        OperationAck ack = new OperationAck(10);
        Assert.assertEquals(user.getDuration(), 1);
        Assert.assertEquals(ack.getDuration(), 1);
        
       

        
        
        //AddReport addreport = new AddReport("client", 10, "reportedClient");
        MessageStoreMessage msgStoreMsg =  new AddLike("client", 10, 10);
        WorkerHelper workerhelper = new WorkerHelper(worker, client, msgStoreMsg, system);

        worker.tell(msgStoreMsg);
        system.tick();
        system.tick();
        system.tick();
        system.tick();
        system.tick();

        workerhelper.tick();
        //WorkerHelper workerhelper1 = new WorkerHelper(worker, client, msgStoreMsg, system);
        worker.tell(msgStoreMsg);
        workerhelper.tick();


        
        //worker.receive(addreport);
      
        
        worker.tell(new FinishCommunication(10));
        worker.tick();
        worker.tell(new FinishCommunication(990000000));
        system.tick();

        
    }

    @Test
    public void wrongCommIDTestDeleteLikeDislike() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new DeleteLikeOrDislike("client", 12, 0, Type.DISLIKE));

        system.tick();
        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }

    @Test
    public void wrongCommIDTestLike() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new Like("client", 12, 0));
        system.tick();

        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }

    @Test
    public void wrongCommIDTestDislike() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new Dislike("client", 12, 0));
        system.tick();

        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }

    @Test
    public void wrongCommIDTestFinishCom() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new FinishCommunication(12));
        system.tick();

        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }

    @Test
    public void wrongCommIDTestSearch() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new SearchMessages("hi",12));
        system.tick();

        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }

    
    @Test
    public void wrongCommIDTestRetrieve() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new RetrieveMessages("client",12));
        system.tick();

        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }

    @Test
    public void wrongCommIDTestReport() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new Report("client",12, "client"));
        system.tick();

        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }
    
    @Test
    public void wrongCommIDTestReaction() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new Reaction("client",12, 10, Emoji.CRYING));
        system.tick();

        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }

    @Test
    public void wrongCommIDTestPub() throws UnknownClientException, UnknownMessageException {

        SimulatedActorSystem system = new SimulatedActorSystem();
        Dispatcher dispatcher = new Dispatcher(system, 2);
        system.spawn(dispatcher);
        TestClient client = new TestClient();
        system.spawn(client);
        
        InitCommunication initComm = new InitCommunication(client, 10);
        dispatcher.tell(initComm);
        
        while (client.receivedMessages.size() == 0)
            system.runFor(1);

        Message initAckMessage = client.receivedMessages.remove();
        Assert.assertEquals(InitAck.class, initAckMessage.getClass());
        InitAck initAck = (InitAck) initAckMessage;
        Assert.assertEquals(Long.valueOf(10), initAck.communicationId);

        SimulatedActor worker = initAck.worker;

        worker.tell(new Publish(new UserMessage("author", "message"), 12));
        system.tick();

        dispatcher.tell(new Stop());

        Assert.assertThrows(UnknownClientException.class, ()->{
            while (system.getActors().size() > 1)
                system.runFor(1);
        });
    }
}
