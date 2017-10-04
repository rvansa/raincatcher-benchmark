package com.feedhenry.raincatcher

import java.util.{Base64, UUID}
import java.util.concurrent.ThreadLocalRandom

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import play.api.libs.json._

import scala.io.Source
import scala.util.Random

class DemoSimulation extends Simulation {

  val titleWords = Array("Foo", "Bar", "Jump", "Run", "Dash") // TODO load longer list from file
  val firstNames = Array("John", "Bill", "Andy", "Dough")
  val familyNames = Array("McCarthy", "Bucks", "Brown", "Lee")
  val SYNC_CONFIG = Source.fromResource("sync-config.json").mkString

  // @formatter:off
  val SYNC_JSON = ("""{
      |"fn":"sync",
      |"dataset_id":"workorders",
      |"query_params":{"assignee":"${user.id}"},
      |"config":""" + SYNC_CONFIG + """,
      |"meta_data":{},
      |"dataset_hash":"${hash}",
      |"acknowledgements":[${acks}],
      |"pending":[${pending}],
      |"__fh":{"cuid":"${cuid}"}}""").stripMargin.replaceAll("\n", "")
  val SYNC_RECORDS_JSON = """{
      |"fn":"syncRecords",
      |"dataset_id":"workorders",
      |"query_params":{"assignee":"${user.id}"},
      |"clientRecs":{},
      |"__fh":{"cuid":"${cuid}"}}""".stripMargin.replaceAll("\n", "")
  // @formatter:on

  val httpProtocol = http
    .baseURL("http://localhost:8001")
    .acceptHeader("*/*")
    .disableAutoReferer
    .disableWarmUp
    .disableFollowRedirect

  def selectWorkflow(str: String): String = {
    val data = (Json.parse(str) \ "data").get.as[JsArray]
    data.value(ThreadLocalRandom.current().nextInt(data.value.length)).toString()
  }

  def randomTitle(): String = {
    val sb = new StringBuilder
    val random = ThreadLocalRandom.current()
    for (i <- 0 to 2) {
      if (!sb.isEmpty) sb.append(' ');
      sb.append(titleWords(random.nextInt(titleWords.length)))
    }
    sb.toString()
  }

  def randomHash(length: Int): String = {
    val random = ThreadLocalRandom.current()
    new Random(random).alphanumeric.take(length).mkString
  }

  def randomItem[T](array: Seq[T]) = {
    array(ThreadLocalRandom.current().nextInt(array.length))
  }

  def alphaUpper(random: Random) {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    alphabet.charAt(random.nextInt(alphabet.length))
  }

  def num(random: Random): Unit = {
    val alphabet = "0123456789"
    alphabet.charAt(random.nextInt(alphabet.length))
  }

  def randomRegNumber(): String = {
    val random = ThreadLocalRandom.current()
    Stream.continually(() => alphaUpper(random)).take(3).mkString + "-" + Stream.continually(() => num(random)).take(4).mkString
  }

  def randomName(): String = {
    randomItem(firstNames) + " " + randomItem(familyNames)
  }

  def randomNum(length: Int): String = {
    val random = ThreadLocalRandom.current()
    Stream.continually(() => num(random)).take(length).mkString
  }

  def createSubmission(code: String): JsValue = {
    code match {
      case "accident-report-form" => JsObject(Seq(
        "regNr" -> JsString(randomRegNumber()),
        "owner" -> JsString(randomName()),
        "phone" -> JsString(randomNum(9))
      ))
      case "generic-signature" => {
        val bytes = new Array[Byte](1000)
        ThreadLocalRandom.current().nextBytes(bytes)
        JsObject(Map("imageURI" -> JsString("data:image/png;base64," + Base64.getEncoder.encodeToString(bytes))))
      }
      case "vehicle-inspection" => JsObject(Seq(
        "tires" -> JsBoolean(ThreadLocalRandom.current().nextBoolean()),
        "lights" -> JsBoolean(ThreadLocalRandom.current().nextBoolean()),
        "fuel" -> JsNumber(ThreadLocalRandom.current().nextInt(100))
      ))
    }
  }

  def selectUser(str: String): Map[String, String] = {
    val users = (Json.parse(str) \ "users").get.as[JsArray]
    val user: JsValue = users.value(ThreadLocalRandom.current().nextInt(users.value.length));
    val id = (user \ "id").get.toString().replaceAll("\"", "")
    val username = (user \ "username").get.toString().replaceAll("\"", "")
    Map("id" -> id, "username" -> username)
  }

  def parseWorkorders(json: String): Seq[JsObject] = {
    (Json.parse(json) \\ "data").map(value => value.as[JsObject])
  }

  def parseChanges(json: String): Seq[Map[String, String]] = {
    val parsed = Json.parse(json);
    val create = (parsed \ "create").map(value => value.as[JsObject].values.map(v => parseChange(v, "create"))).get
    val update = (parsed \ "update").map(value => value.as[JsObject].values.map(v => parseChange(v, "update"))).get
    val delete = (parsed \ "delete").map(value => value.as[JsObject].values.map(v => parseChange(v, "delete"))).get
    (create ++ update ++ delete).toSeq
  }

  def parseChange(v: JsValue, changeType: String): Map[String, String] = {
    val workorder = v.as[JsObject]
    Map(
      ("_id" -> (workorder \ "_id").as[String]),
      ("uid" -> (workorder \ "uid").as[String]),
      ("type" -> changeType)
    )
  }

  def createPending(pre: JsObject, post: JsObject) = {
    // @formatter:off
    ("""{
      |"action":"update",
      |"inFlight":"true",
      |"inFlightDate":""" + System.currentTimeMillis() + """,
      |"hash":"""" + randomHash(40) + """",
      |"pre":""" + pre + """,
      |"preHash":"""" + randomHash(40) + """",
      |"post":""" + post + """,
      |"postHash":"""" + randomHash(40) + """",
      |"timestamp":""" + System.currentTimeMillis() + """,
      |"uid":"""" + (pre \ "id").as[String] + """"}"""
    ).stripMargin.replaceAll("\n", "")
    // @formatter:on
  }

  val portal = scenario("Portal")
    .exec(http("Cookie login")
      .post("/cookie-login")
      .formParam("username", "max").formParam("password", "123").formParam("login", "Submit")
      .check(status.is(302)))
    /* Load and drop completed workorders */
    .exec(http("Load workorders")
      .get("/api/workorders")
      .queryParam("size", "100000")
      .check(jsonPath("$.data[?(@.status=='Complete')].id").findAll.optional.saveAs("completed-ids")))
    .doIf("${completed-ids.exists()}") {
      foreach("${completed-ids}", "completed-id") {
        exec(http("Delete workorder")
          .delete("/api/workorders/${completed-id}"))
      }
    }
    /* Pick random workflow and create new workorder assigning it to random user */
    .exec(http("Load workflows")
      .get("/api/workflows")
      // jsonPath strips escape characters from the quotes in embedded XML, so we need to use play to parse it
      //      .check(jsonPath("$.data[*]").findAll
      //        .transform(array => array(ThreadLocalRandom.current().nextInt(array.length)))
      //        .saveAs("selected-workflow")))
      .check(bodyString.transform(json => selectWorkflow(json)).saveAs("selected-workflow")))
    .exitHereIfFailed
    .exec(http("Load users")
      .get("/api/users")
      .queryParam("filter", " ") /* all users have whitespace in name */
        .check(jsonPath("$.users[*].id")
          .transform(array => randomItem(array))
          .saveAs("selected-assignee")))
    .exitHereIfFailed
    .exec(s => {
      s.setAll(
        "workflow-title" -> randomTitle(),
        "workflow-id" -> randomHash(9)
      )
    })
    .exec(http("New workorder")
      .post("/api/workorders")
      .body(StringBody(
        """{
          |"workflow": ${selected-workflow},
          |"assignee":"${selected-assignee}",
          |"title":"${workflow-title}",
          |"id":"${workflow-id}",
          |"results":[]}
         """.stripMargin)).asJSON
      .check(status.is(200)))

  val mobile = scenario("Mobile")
    /* Start with portal-like query to grab random users */
    .exec(http("Cookie login")
      .post("/cookie-login")
      .formParam("username", "max").formParam("password", "123").formParam("login", "Submit")
      .check(status.is(302)))
    .exec(http("Load users")
      .get("/api/users")
      .queryParam("filter", " ") /* all users have whitespace in name */
      /* Can't get jsonPath here working either */
      .check(bodyString.transform(json => selectUser(json)).saveAs("user")))
    .exec(flushHttpCache)
    .exitHereIfFailed
    /* The mobile part starts here */
    .exec(http("Login")
      .post("/token-login")
      .formParam("username", "${user.username}")
      .formParam("password", "123")
      .check(jsonPath("$.token").saveAs("token")))
    .exec(s => s.setAll(
      "hash" -> "no-hash",
      "pending" -> "",
      "acks" -> "",
      "cuid" -> UUID.nameUUIDFromBytes(s("user").as[Map[String, String]].apply("username").getBytes())
    ))
    .forever() {
      pace(Options.syncPeriod)
      .exec(http("Sync")
        .post("/sync/workorders")
        .header(HttpHeaderNames.Authorization, "JWT ${token}")
        .body(StringBody(SYNC_JSON)).asJSON
        .check(jsonPath("$.hash").find.optional.saveAs("newhash")))
      /* Sometimes we don't get new hash in the response.
       If we have the hash and it does not match our previous hash, run sync records */
      .doIf("${newhash.exists()}") {
        doIfEqualsOrElse("${hash}", "${newhash}") {
          exec(s => s) /* noop */
        } {
          exec(http("Sync records")
            .post("/sync/workorders")
            .header(HttpHeaderNames.Authorization, "JWT ${token}")
            .body(StringBody(SYNC_RECORDS_JSON)).asJSON
            .check(jsonPath("$.hash").saveAs("hash"))
            .check(bodyString.transform(json => parseWorkorders(json)).saveAs("workorders"))
            .check(bodyString.transform(json => parseChanges(json)).saveAs("changes")))
            /* Compute which create/update/delete should we confirm in next sync loop */
            .exec(s => s.set("acks", s("changes").as[Seq[Map[String, String]]]
              // @formatter:off
              .map(change => ("""{
                |"_id":"""" + change("_id") + """",
                |"cuid":"""" + s("cuid").as[String] + """",
                |"hash":"""" + randomHash(40) + """",
                |"type":"applied",
                |"action":"""" + change("type") + """",
                |"uid":"""" + change("uid") + """",
                |"msg":null,
                |"timestamp":"""" + System.currentTimeMillis() + """"
                |}""").stripMargin.replaceAll("\n", ""))
              // @formatter:on
              .mkString(",")))
          .exec(s => s.setAll("pending" -> "", "acks" -> ""))
          .exitHereIfFailed
        }
      }
      .doIf(s => ThreadLocalRandom.current().nextDouble < Options.modProbability) {
        doIfOrElse("${pendingWorkorder.isUndefined()}") {
          /* Start processing a new workorder */
          exec(s => {
            val newWorkOrders = s("workorders").as[Seq[JsObject]].filter(workorder => {
              val status = workorder \ "status"
              status.isEmpty || "New".equals(status.as[String]);
            }).toArray
            if (!newWorkOrders.isEmpty) {
              val workorder = randomItem(newWorkOrders)
              printf("%s: Starting filling out %s (%s)%n", s("user").as[Map[String, String]].apply("username"),
                (workorder \ "title").as[String], (workorder \ "id").as[String])
              val workflow = (workorder \ "workflow").get
              val stepIds = workflow \ "steps" \\ "id";
              val nextStepId = randomItem(stepIds)
              val post = workorder + ("status" -> JsString("Pending")) + ("currentStep" -> nextStepId)
              val pending = createPending(workorder, post)
              s.setAll(
                ("pending" -> pending),
                ("pendingWorkorder" -> post),
                ("workflow" -> workflow)
              )
            } else {
              printf("%s: No workorders to process%n", s("user").as[Map[String, String]].apply("username"))
              s
            }
          })
        } {
          /* Complete unfinished workorder */
          exec(s => {
            val pre = s("pendingWorkorder").as[JsObject]
            printf("%s: Completing %s (%s)%n", s("user").as[Map[String, String]].apply("username"),
              (pre \ "title").as[String], (pre \ "id").as[String])
            val workflow = s("workflow").as[JsObject]
            val steps = (workflow \ "steps").as[Seq[JsObject]]
            val results = steps.map(step => JsObject(Seq(
              ("stepId" -> (step \ "id").get),
              ("submission" -> createSubmission((step \ "code").as[String])),
              ("timestamp" -> JsNumber(System.currentTimeMillis())),
              ("submitter" -> JsString(s("user").as[Map[String, String]].apply("id")))
            )))
            val post = pre + ("status" -> JsString("Complete")) - "currentStep" + ("results" -> JsArray(results))
            s.set("pending", createPending(pre, post)).removeAll("pendingWorkorder", "workflow")
          })
        }
      }
    }

  setUp(
    portal.inject(constantUsersPerSec(Options.workordersPerSec) during Options.testDuration).protocols(httpProtocol),
    mobile.inject(rampUsers(Options.mobileUsers) over Options.testRampUp).protocols(httpProtocol)
  ).maxDuration(Options.testDuration)
}
