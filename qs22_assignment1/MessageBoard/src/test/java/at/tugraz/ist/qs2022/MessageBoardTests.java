package at.tugraz.ist.qs2022;

import at.tugraz.ist.qs2022.actorsystem.DeterministicChannel;
import at.tugraz.ist.qs2022.actorsystem.Message;
import at.tugraz.ist.qs2022.actorsystem.SimulatedActor;
import at.tugraz.ist.qs2022.actorsystem.SimulatedActorSystem;
import at.tugraz.ist.qs2022.messageboard.*;
import at.tugraz.ist.qs2022.messageboard.clientmessages.*;
import at.tugraz.ist.qs2022.messageboard.dispatchermessages.Stop;
import at.tugraz.ist.qs2022.messageboard.dispatchermessages.StopAck;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.AddDislike;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.AddLike;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.DeleteLikeOrDislike;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.MessageStoreMessage;
import at.tugraz.ist.qs2022.messageboard.messagestoremessages.UpdateMessageStore;
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
        dispatcher.tell(new Stop());
        //system.runFor(2);

        // TODO: run system until workers and dispatcher are stopped

        while (system.getActors().size() > 1)
            system.runFor(1);

    }

    // TODO: Implement test cases

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

        StopAck stopAck = new StopAck(worker);
        Assert.assertEquals(2, stopAck.getDuration());
        
        Publish publish = new Publish(usermessage, 10);
        Like like = new Like("client", 10, usermessage.getMessageId());
        Dislike dislike = new Dislike("client", 10, usermessage.getMessageId());

        worker.tell(publish);        
        worker.tell(like);
        worker.tell(dislike);

        for(int i = 0; i < 1000; i++)
            worker.tick();
        
        List<Message> expected_message_log = Arrays.asList(initComm, publish, like, dislike);
        Assert.assertEquals(expected_message_log, worker.getMessageLog());

        /*
        if (worker.getMessageLog().size() <= 3)
            assert(false);
        */

        //would be meaningful if I'd get messages to be processed
        //Assert.assertEquals(11, usermessage.getPoints());

        //could be more meaningful?
        Assert.assertEquals(0, usermessage.getLikes().size());

        //could be more meaningful?
        Assert.assertEquals(0, usermessage.getDislikes().size());

        //could be more meaningful?
        Assert.assertEquals(0, usermessage.getReactions().size());

        Assert.assertEquals("author:message liked by : disliked by :; Points: 10", usermessage.toString());
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


    //@Test
    //public void testDispatcher() throws UnknownClientException, UnknownMessageException {
//
    //    SimulatedActorSystem system = new SimulatedActorSystem();
    //    Dispatcher dispatcher = new Dispatcher(system, 2);
    //    system.spawn(dispatcher);
    //    TestClient client = new TestClient();
    //    system.spawn(client);
//
    //    // send request and run system until a response is received
    //    // communication id is chosen by clients
    //    dispatcher.tell(new InitCommunication(client, 10));
    //    while (client.receivedMessages.size() == 0)
    //        system.runFor(1);
//
    //    Message initAckMessage = client.receivedMessages.remove();
    //    Assert.assertEquals(InitAck.class, initAckMessage.getClass());
    //    InitAck initAck = (InitAck) initAckMessage;
    //    Assert.assertEquals(Long.valueOf(10), initAck.communicationId);
//
    //    SimulatedActor worker = initAck.worker;
//
    //    // end the communication
    //    worker.tell(new Stop());
    //    //while (client.receivedMessages.size() == 0)
    //    //    system.runFor(1);
//
    //    //Message finAckMessage = client.receivedMessages.remove();
    //    //Assert.assertEquals(FinishAck.class, finAckMessage.getClass());
    //    //FinishAck finAck = (FinishAck) finAckMessage;
//
    //    //Assert.assertEquals(Long.valueOf(10), finAck.communicationId);
    //    //dispatcher.tell(new Stop());
//
    //    // TODO: run system until workers and dispatcher are stopped
//
    //    while (system.getActors().size() >= 0)
    //        system.runFor(1);
    //}
}