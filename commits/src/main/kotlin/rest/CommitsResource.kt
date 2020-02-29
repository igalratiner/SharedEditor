package rest

import aurthorization.rolesAllowed
import authentication.TEXT_ACCESS_AUTH
import authentication.textRequest
import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.websocket.webSocket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.selectUnbiased
import mu.KotlinLogging
import org.koin.core.parameter.parametersOf
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import pojo.Commit
import pojo.CommitRequest
import pojo.TextRole
import services.CommitsNotification
import services.CommitsService
import java.lang.RuntimeException

@UseExperimental(InternalCoroutinesApi::class)
@ExperimentalCoroutinesApi
fun Route.commits() {
    val commitsService: CommitsService = get()
    val gson = Gson()
    val logger = KotlinLogging.logger {}

    fun Any.toJson(): String {
        return gson.toJson(this)
    }

    suspend fun SendChannel<Frame>.send(commit: Commit) {
        send(Frame.Text(commit.toJson()))
    }

    authenticate(TEXT_ACCESS_AUTH) {

        get {
            val requestedCommits = call.receive<List<CommitRequest>>()
            call.respond(commitsService.getCommits(call.textRequest!!.textDetails.hash, requestedCommits))
        }

        rolesAllowed(TextRole.OWNER) {
            post {
                logger.info { "creation of commits requested" }
                val textHash = call.textRequest!!.textDetails.hash

                logger.info { "creation of commits requested for text hash: $textHash" }

                if (commitsService.createTextCommits(textHash)) {
                    call.respond(HttpStatusCode.Created, "Created")
                } else {
                    call.respond(HttpStatusCode.Found, "commits queue exists already")
                }
            }

            delete {
                val textHash = call.textRequest!!.textDetails.hash
                logger.info { "deletion of commits of textHash=$textHash requested" }
                commitsService.deleteCommits(textHash)
                call.respond("Text hash=$textHash was deleted")
            }
        }

    }

    webSocket("/hash/{HASH}/role/{ROLE}") { // todo not secured at all validation needed
        val textHash = call.parameters["HASH"]!!
        val textRole = TextRole.valueOf(call.parameters["ROLE"]!!)
        logger.info { "hash: $textHash and role: $textRole" }

        val websocketSession = this

        val notificationsChannel = commitsService.connect(textHash)

        commitsService.getCommits(textHash).forEach { outgoing.send(it) }

        try {
            while(true) {
                selectUnbiased<Unit> {
                    notificationsChannel.onReceive {
                        when (it) {
                            is CommitsNotification.NextCommit -> {
                                outgoing.send(it.commit)
                            }
                            is CommitsNotification.IllegalCommit -> {
                                notificationsChannel.cancel()
                                websocketSession.close()
                            }
                            is CommitsNotification.TextCommitsNotExist -> {
                                notificationsChannel.cancel()
                                websocketSession.close()
                            }
                        }
                    }
//                incoming.onReceiveOrClosed {
//                    when(it.valueOrNull) {
//                        null -> throw RuntimeException("websocket session was closed")
//                        is Frame ->
//                            if (textRole.writePrivileges()) {
//                            when (it.value) {
//                                is Frame.Text -> {
//                                    val msg = (it.value as Frame.Text).readText()
//                                    logger.info { "message came: $msg" }
//                                    val commit = gson.fromJson(msg, Commit::class.java)
//                                    logger.info { "message parsed from json : $commit" }
//
//                                    commitsService.commitIncomingChannel.send(textHash to commit)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
                    incoming.onReceive {
                        if (textRole.writePrivileges()) {
                            val msg = (it as Frame.Text).readText()
                            logger.info { "message came: $msg" }
                            val commit = gson.fromJson(msg, Commit::class.java)
                            logger.info { "message parsed from json : $commit" }
                            commitsService.commitIncomingChannel.send(textHash to commit)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            commitsService.deleteOldChannelsChannel.send(textHash to notificationsChannel)
            logger.error { "Websocket ended with ${e.cause}" }
        }
    }
}
