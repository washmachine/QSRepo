package at.tugraz.ist.qs2022

import at.tugraz.ist.qs2022.actorsystem.{Message, SimulatedActor}
import at.tugraz.ist.qs2022.messageboard.MessageStore.USER_BLOCKED_AT_COUNT
import at.tugraz.ist.qs2022.messageboard.UserMessage
import at.tugraz.ist.qs2022.messageboard.Worker.MAX_MESSAGE_LENGTH
import at.tugraz.ist.qs2022.messageboard.clientmessages._
import org.junit.runner.RunWith
import org.scalacheck.Prop.{classify, forAll}
import org.scalacheck.{Gen, Properties}


import scala.jdk.CollectionConverters._

@RunWith(classOf[ScalaCheckJUnitPropertiesRunner])
class MessageBoardProperties extends Properties("MessageBoardProperties") {

  val validMessageGen: Gen[String] = Gen.asciiPrintableStr.map(s =>
    if (s.length <= MAX_MESSAGE_LENGTH) s else s.substring(0, MAX_MESSAGE_LENGTH)
  )
  property("message length: Publish + Ack [R1]") = forAll { (author: String, message: String) =>
    // arrange-  initialize the message board
    val sut = new SUTMessageBoard
    sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
    val worker: SimulatedActor = initAck.worker

    // act - send and receive the messages
    worker.tell(new Publish(new UserMessage(author, message), sut.getCommId))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val reply = sut.getClient.receivedMessages.remove()

    worker.tell(new FinishCommunication(sut.getCommId))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]

    // assert - define your property and check against it
    // The following classify is optional, it prints stats on the generated values.
    // But the check inside is required.
    classify(message.length <= MAX_MESSAGE_LENGTH, "valid message length", "invalid message length") {
      // if operationAck is received, the message length should be smaller or equal to 10
      reply.isInstanceOf[OperationAck] == message.length <= MAX_MESSAGE_LENGTH
    }
  }
  // TODO: add another properties for requirements R1-R13


  /////////DOESNT WORK
  property("retrieve list of all existing messages of author [R5]") = 
    forAll(Gen.alphaStr, Gen.nonEmptyListOf(validMessageGen)) { (author: String, messages: List[String]) =>
        // arrange-  initialize the message board
        val sut = new SUTMessageBoard
        sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
        while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
        val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
        val worker: SimulatedActor = initAck.worker

        var i = 0;
        var valid = true;

        while (i < messages.length && valid == true){
          val msg = messages(i)
          
          // act - send and receive the messages
          worker.tell(new Publish(new UserMessage(author, msg), sut.getCommId))
          while (sut.getClient.receivedMessages.isEmpty)
            sut.getSystem.runFor(1)
          val reply = sut.getClient.receivedMessages.remove()

          valid = reply.isInstanceOf[OperationAck]
          i += 1
        }

        worker.tell(new RetrieveMessages(author, sut.getCommId))
        while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
        val allMessages = sut.getClient.receivedMessages.remove()
        val messagesOfAuthor = allMessages.asInstanceOf[FoundMessages].messages.asScala.filter(m => m.getAuthor.equals(author))

        worker.tell(new FinishCommunication(sut.getCommId))
        while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
        sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]

        classify(allMessages.isInstanceOf[FoundMessages], "valid message", "invalid message"){
            classify(messagesOfAuthor.length == messages.length, "found the message", "didn't find the message"){
                messagesOfAuthor.length == messages.length && allMessages.isInstanceOf[FoundMessages]
            }
        }
  }



  property("retrieve all messages with searches [R6]") = 
    forAll(Gen.alphaStr, Gen.nonEmptyListOf(validMessageGen), validMessageGen) { (author: String, messages: List[String], searchString: String) =>
        val sut = new SUTMessageBoard
        sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
        while (sut.getClient.receivedMessages.isEmpty)
            sut.getSystem.runFor(1)
        val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
        val worker: SimulatedActor = initAck.worker

        var valid = true
        var i = 0
        while (i < messages.length && valid == true) {
            val msg = messages(i)
            // act - send and receive the messages
            worker.tell(new Publish(new UserMessage(author, msg), sut.getCommId))
            while (sut.getClient.receivedMessages.isEmpty)
                sut.getSystem.runFor(1)
            val reply = sut.getClient.receivedMessages.remove()

            valid = reply.isInstanceOf[OperationAck]
            i += 1
        }

        worker.tell(new SearchMessages(searchString, sut.getCommId))
        while (sut.getClient.receivedMessages.isEmpty)
            sut.getSystem.runFor(1)
        val allMessages = sut.getClient.receivedMessages.remove()

        var testMessages = messages.filter(m => m.toLowerCase.contains(searchString.toLowerCase))
        if (author.toLowerCase.contains(searchString.toLowerCase)) {
            testMessages = (testMessages ++ messages).distinct
        }

        val foundMessages = allMessages.asInstanceOf[FoundMessages].messages.asScala

        worker.tell(new FinishCommunication(sut.getCommId))
        while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
        sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]
      
        classify(allMessages.isInstanceOf[FoundMessages], "valid message", "invalid message") {
            classify(testMessages.length == foundMessages.length, "found the message", "didn't find the message") {
                testMessages.length == foundMessages.length && allMessages.isInstanceOf[FoundMessages]
            }
        }
  }

  property("report user after having reported user already [R7]") = 
    forAll(Gen.alphaStr, Gen.alphaStr) { (author1: String, author2: String) =>
        val sut = new SUTMessageBoard
        sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
        while (sut.getClient.receivedMessages.isEmpty)
            sut.getSystem.runFor(1)
        val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
        val worker: SimulatedActor = initAck.worker

        worker.tell(new Report(author2, sut.getCommId, author1))
        while (sut.getClient.receivedMessages.isEmpty)
            sut.getSystem.runFor(1)
        val report1 = sut.getClient.receivedMessages.remove()

        worker.tell(new Report(author2, sut.getCommId, author1))
        while (sut.getClient.receivedMessages.isEmpty)
            sut.getSystem.runFor(1)
        val report2 = sut.getClient.receivedMessages.remove()

        worker.tell(new FinishCommunication(sut.getCommId))
        while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
        sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]

        classify(report1.isInstanceOf[OperationAck], "valid report1", "invalid report1") {
            classify(report2.isInstanceOf[OperationFailed], "valid report2", "invalid report2") {
                report1.isInstanceOf[OperationAck] && report2.isInstanceOf[OperationFailed]
            }
        }
  }
}

