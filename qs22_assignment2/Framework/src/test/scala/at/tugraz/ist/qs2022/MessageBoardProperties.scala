package at.tugraz.ist.qs2022

import at.tugraz.ist.qs2022.actorsystem.{Message, SimulatedActor}
import at.tugraz.ist.qs2022.messageboard.MessageStore.USER_BLOCKED_AT_COUNT
import at.tugraz.ist.qs2022.messageboard.UserMessage
import at.tugraz.ist.qs2022.messageboard.Worker.MAX_MESSAGE_LENGTH
import at.tugraz.ist.qs2022.messageboard.clientmessages._
import org.junit.runner.RunWith
import org.scalacheck.Prop.{classify, forAll}
import org.scalacheck.{Gen, Properties}
import org.scalacheck.Prop.propBoolean

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
  property("example property with generators") =
    forAll(Gen.alphaStr, Gen.nonEmptyListOf(validMessageGen)) { (author: String, messages: List[String]) =>
      val sut = new SUTMessageBoard
      sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
      val worker: SimulatedActor = initAck.worker

      // here would be a worker.tell, e.g. in a loop

      worker.tell(new FinishCommunication(sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      sut.getClient.receivedMessages.remove()

      // here would be a check
      true
  }
  // TODO: add another properties for requirements R1-R13

  property("[R2] save message if not already saved") = forAll(Gen.alphaStr, validMessageGen) { (author: String, message: String) => 
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

    worker.tell(new Publish(new UserMessage(author, message), sut.getCommId))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val duplicate_reply = sut.getClient.receivedMessages.remove()

    worker.tell(new FinishCommunication(sut.getCommId))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]

    classify(reply == duplicate_reply, "same message", "different message") {
      reply.isInstanceOf[OperationAck] && duplicate_reply.isInstanceOf[OperationFailed]
    }
  }

  property("[R3] check if message exist before reacting") = forAll(Gen.alphaStr, Gen.alphaStr, validMessageGen) { (author: String, author2: String, message: String) =>
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

    worker.tell(new RetrieveMessages(author, sut.getCommId))
    while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
    val msg_to_react_to = sut.getClient.receivedMessages.remove()

    worker.tell(new Like(author2, sut.getCommId, msg_to_react_to.asInstanceOf[FoundMessages].messages.asScala.filter(m => m.getMessage.equals(message)).head.getMessageId))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val liked_reply = sut.getClient.receivedMessages.remove()
    
    worker.tell(new Like(author, sut.getCommId, -1)) // messageID = -1 to gurantee its invalid
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val liked_reply_invalid_msg = sut.getClient.receivedMessages.remove()

    worker.tell(new Dislike(author, sut.getCommId, msg_to_react_to.asInstanceOf[FoundMessages].messages.get(0).getMessageId))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val disliked_reply = sut.getClient.receivedMessages.remove()

    worker.tell(new Dislike(author, sut.getCommId, -1)) // messageID = -1 to gurantee its invalid
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val disliked_reply_invalid_msg = sut.getClient.receivedMessages.remove()

    worker.tell(new Reaction(author, sut.getCommId, msg_to_react_to.asInstanceOf[FoundMessages].messages.get(0).getMessageId, Reaction.Emoji.SMILEY))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val reaction_reply = sut.getClient.receivedMessages.remove()

    worker.tell(new Reaction(author, sut.getCommId, -1, Reaction.Emoji.SMILEY)) // messageID = -1 to gurantee its invalid
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
    val reaction_reply_invalid_msg = sut.getClient.receivedMessages.remove()

    worker.tell(new FinishCommunication(sut.getCommId))
    while (sut.getClient.receivedMessages.isEmpty)
      sut.getSystem.runFor(1)
      sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]
    

    
    liked_reply_invalid_msg.isInstanceOf[OperationFailed] && liked_reply.isInstanceOf[ReactionResponse] &&
    disliked_reply_invalid_msg.isInstanceOf[OperationFailed] && disliked_reply.isInstanceOf[ReactionResponse] &&
    reaction_reply_invalid_msg.isInstanceOf[OperationFailed] && reaction_reply.isInstanceOf[ReactionResponse]
  }

    /////////DOESNT WORK
  property("retrieve list of all existing messages of author [R5]") = 
    forAll(Gen.alphaStr, Gen.nonEmptyListOf(validMessageGen)) { (author: String, messages: List[String]) =>
      (author.trim.nonEmpty && messages.forall(x => x.trim.nonEmpty)) ==> {
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
  }

  property("retrieve all messages with searches [R6]") = 
    forAll(Gen.alphaStr, Gen.nonEmptyListOf(validMessageGen), validMessageGen) { (author: String, messages: List[String], searchString: String) =>
      (author.trim.nonEmpty && messages.forall(x => x.trim.nonEmpty)) ==> { 
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

  property("[R4] A message may only be liked by users who have not yet liked the corresponding message.") =
    forAll(Gen.alphaStr, Gen.alphaStr, validMessageGen) { (author_msg: String, author_like: String, message: String) =>
      (author_msg.trim.nonEmpty && author_like.trim.nonEmpty && !author_msg.eq(author_like) && message.trim.nonEmpty) ==> {
      val sut = new SUTMessageBoard
      sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
      val worker: SimulatedActor = initAck.worker

      val msg = new UserMessage(author_msg, message)
      worker.tell(new Publish(msg, sut.getCommId))

      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val reply = sut.getClient.receivedMessages.remove()

      worker.tell(new Like(author_like, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val reply_like = sut.getClient.receivedMessages.remove()

      worker.tell(new Like(author_like, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val reply_like1 = sut.getClient.receivedMessages.remove()

      worker.tell(new FinishCommunication(sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]


      classify(reply_like != null && reply_like.isInstanceOf[ReactionResponse], "first like valid", "first like invalid") {
        classify(reply_like1 != null && reply_like1.isInstanceOf[OperationFailed], "second like valid", "second like invalid") {
            reply_like.isInstanceOf[ReactionResponse] && reply_like1.isInstanceOf[OperationFailed] 
        }
      }
    }
  }

  property("[R4] A message may only be disliked by users who have not yet disliked the corresponding message.") =
    forAll(Gen.alphaStr, Gen.alphaStr, validMessageGen) { (author_msg: String, author_dislike: String, message: String) =>
      (author_msg.trim.nonEmpty && author_dislike.trim.nonEmpty && !author_msg.eq(author_dislike) && message.trim.nonEmpty) ==> {
      val sut = new SUTMessageBoard
      sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
      val worker: SimulatedActor = initAck.worker

      val msg = new UserMessage(author_msg, message)
      worker.tell(new Publish(msg, sut.getCommId))

      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val reply = sut.getClient.receivedMessages.remove()

      worker.tell(new Dislike(author_dislike, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val reply_dislike = sut.getClient.receivedMessages.remove()

      worker.tell(new Dislike(author_dislike, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val reply_dislike1 = sut.getClient.receivedMessages.remove()

      worker.tell(new FinishCommunication(sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]


      classify(reply_dislike != null && reply_dislike.isInstanceOf[ReactionResponse], "first dislike valid", "first dislike invalid") {
        classify(reply_dislike1 != null && reply_dislike1.isInstanceOf[OperationFailed], "second dislike valid", "second dislike invalid") {
            reply_dislike.isInstanceOf[ReactionResponse] && reply_dislike1.isInstanceOf[OperationFailed] 
        }
      }
    }
  }

  property("[R8] If a user has been reported at least USER BLOCKED AT COUNT (= 6) times, he/she cannot send any further Publish, Like, Dislike or Report messages.") =
    forAll(Gen.listOfN(100, Gen.alphaStr).map(_.distinct.take(7)), validMessageGen, validMessageGen) { (authors: List[String], message: String, message0: String) =>
      (authors.forall(x => x.trim.nonEmpty) && message.trim.nonEmpty && message0.trim.nonEmpty) ==> {
      val sut = new SUTMessageBoard
      sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
      val worker: SimulatedActor = initAck.worker

      
      val msg = new UserMessage(authors(6), message0)
      //not banned yet
      worker.tell(new Publish(msg, sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
      val reply = sut.getClient.receivedMessages.remove()

      
      //bann 
      val author_banned = authors(6)
      for (i <- 0 until USER_BLOCKED_AT_COUNT) {
        worker.tell(new Report(authors(i), sut.getCommId, author_banned))
        while (sut.getClient.receivedMessages.isEmpty)
            sut.getSystem.runFor(1)
        val replyReport = sut.getClient.receivedMessages.remove()
        assert(replyReport.isInstanceOf[OperationAck]) // if assert hits -> Gen of 7 unique authors failed
      }

      worker.tell(new Publish(new UserMessage(author_banned, message), sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
      val reply_publish = sut.getClient.receivedMessages.remove()

      worker.tell(new Like(author_banned, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
      val reply_like = sut.getClient.receivedMessages.remove()

      worker.tell(new Dislike(author_banned, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
      val reply_dislike = sut.getClient.receivedMessages.remove()

      worker.tell(new Report(author_banned, sut.getCommId, authors(0)))
      while (sut.getClient.receivedMessages.isEmpty)
          sut.getSystem.runFor(1)
      val reply_report = sut.getClient.receivedMessages.remove()

      worker.tell(new FinishCommunication(sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]

      classify(reply_publish.isInstanceOf[UserBanned], "success: banned author couldnt Publish", "failure: banned author was able to Publish") {
        classify(reply_like.isInstanceOf[UserBanned], "success: banned author couldnt Like", "failure: banned author was able to Like") {
          classify(reply_dislike.isInstanceOf[UserBanned], "success: banned author couldnt Dislike", "failure: banned author was able to Dislike") {
            classify(reply_report.isInstanceOf[UserBanned], "success: banned author couldnt Report", "failure: banned author was able to Report") {
              reply_publish.isInstanceOf[UserBanned] && reply_like.isInstanceOf[UserBanned] && reply_dislike.isInstanceOf[UserBanned] && reply_report.isInstanceOf[UserBanned]
            }
          }
        }
      }
    }
  }

  property("[R9] Successful requests should be confirmed by sending OperationAck or ReactionResponse (depending on request type).") =
    forAll(Gen.alphaStr, Gen.alphaStr, validMessageGen) { (author: String, author_reported: String, message: String) =>
      (author.trim.nonEmpty && author_reported.trim.nonEmpty&& message.trim.nonEmpty) ==> {
      val sut = new SUTMessageBoard
      sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
      val worker: SimulatedActor = initAck.worker

      //Requests are considered successful when a message has been saved, a Like or Dislike has been added to a message, or a report for an author has been added
      val msg = new UserMessage(author, message)
      worker.tell(new Publish(msg, sut.getCommId))

      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val reply_publish = sut.getClient.receivedMessages.remove()

      worker.tell(new Like(author, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)

      val reply_like = sut.getClient.receivedMessages.remove()


      worker.tell(new Dislike(author, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)

      val reply_dislike = sut.getClient.receivedMessages.remove()

      worker.tell(new Report(author, sut.getCommId, author_reported))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)

      val reply_report = sut.getClient.receivedMessages.remove()

      
      worker.tell(new FinishCommunication(sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]

      //points default is 0
      classify(reply_publish.isInstanceOf[OperationAck] , "publish successful", "publish unsuccessful"){
        classify(reply_like.isInstanceOf[ReactionResponse],  "like successfully ", "like unsuccessful") {
          classify(reply_dislike.isInstanceOf[ReactionResponse] , "dislike successful", "dislike unsuccessful"){
            classify(reply_report.isInstanceOf[OperationAck],  "like successfully ", "like unsuccessful") {
              reply_publish.isInstanceOf[OperationAck] && reply_like.isInstanceOf[ReactionResponse] && reply_dislike.isInstanceOf[ReactionResponse] && reply_report.isInstanceOf[OperationAck]
            }
          }
        }
      }
    }
  }
  
  property("[R11] If a message has been liked, two points should be added to the messages points counter. If a message has been disliked, one point should be removed.") =
    forAll(Gen.alphaStr, Gen.alphaStr, validMessageGen) { (author_msg: String, author_dislike: String, message: String) =>
      (author_msg.trim.nonEmpty && author_dislike.trim.nonEmpty && !author_msg.eq(author_dislike) && message.trim.nonEmpty) ==> {
      val sut = new SUTMessageBoard
      sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
      val worker: SimulatedActor = initAck.worker

      val msg = new UserMessage(author_msg, message)
      worker.tell(new Publish(msg, sut.getCommId))

      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      val reply = sut.getClient.receivedMessages.remove()

      worker.tell(new Dislike(author_dislike, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)

      val reply_dislike = sut.getClient.receivedMessages.remove().asInstanceOf[ReactionResponse]
      val reply_dislike_points = reply_dislike.points

      worker.tell(new Like(author_msg, sut.getCommId, msg.getMessageId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)

      val reply_like = sut.getClient.receivedMessages.remove().asInstanceOf[ReactionResponse]
      val reply_like_points = reply_like.points

      worker.tell(new FinishCommunication(sut.getCommId))
      while (sut.getClient.receivedMessages.isEmpty)
        sut.getSystem.runFor(1)
      sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]

      //points default is 0
      classify(reply_dislike.isInstanceOf[ReactionResponse] && reply_dislike_points == -1  , "dislike successfully reduced points by one", "dislike unsuccessful"){
        classify(reply_like.isInstanceOf[ReactionResponse] && reply_like_points == 1 ,  "like successfully increased points by two", "like unsuccessful") {
            reply_like_points == 1 && reply_dislike_points == -1
        }
      }
    }
  }



  //property("[R13] A dislike may only be deleted if message was disliked before.") =
  //  forAll(Gen.alphaStr, Gen.alphaStr, validMessageGen) { (author_msg: String, author_dislike: String, message: String) =>
  //    (author_msg.trim.nonEmpty && author_dislike.trim.nonEmpty && !author_msg.eq(author_dislike) && message.trim.nonEmpty) ==> {
  //    val sut = new SUTMessageBoard
  //    sut.getDispatcher.tell(new InitCommunication(sut.getClient, sut.getCommId))
  //    while (sut.getClient.receivedMessages.isEmpty)
  //      sut.getSystem.runFor(1)
  //    val initAck = sut.getClient.receivedMessages.remove.asInstanceOf[InitAck]
  //    val worker: SimulatedActor = initAck.worker
//
  //    val msg = new UserMessage(author_msg, message)
  //    worker.tell(new Publish(msg, sut.getCommId))
//
  //    while (sut.getClient.receivedMessages.isEmpty)
  //      sut.getSystem.runFor(1)
  //    val reply = sut.getClient.receivedMessages.remove()
//
  //    worker.tell(new Dislike(author_dislike, sut.getCommId, msg.getMessageId))
  //    while (sut.getClient.receivedMessages.isEmpty)
  //      sut.getSystem.runFor(1)
  //    val reply_dislike = sut.getClient.receivedMessages.remove()
//
//
//
  //    worker.tell(new FinishCommunication(sut.getCommId))
  //    while (sut.getClient.receivedMessages.isEmpty)
  //      sut.getSystem.runFor(1)
  //    sut.getClient.receivedMessages.remove.asInstanceOf[FinishAck]
//
//
  //    classify(reply_dislike != null && reply_dislike.isInstanceOf[ReactionResponse], "first dislike valid", "first dislike invalid") {
  //      classify(reply_dislike1 != null && reply_dislike1.isInstanceOf[OperationFailed], "second dislike valid", "second dislike invalid") {
  //          reply_dislike.isInstanceOf[ReactionResponse] && reply_dislike1.isInstanceOf[OperationFailed] 
  //      }
  //    }
  //  }
  //}
}



