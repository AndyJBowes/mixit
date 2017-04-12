package mixit.web.handler

import mixit.MixitProperties
import mixit.model.*
import mixit.repository.TalkRepository
import mixit.repository.UserRepository
import mixit.util.*
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.ServerResponse.*
import org.springframework.web.util.UriUtils
import reactor.core.publisher.toMono
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.LocalDate
import kotlin.streams.toList


@Component
class TalkHandler(val repository: TalkRepository,
                  val userRepository: UserRepository,
                  val markdownConverter: MarkdownConverter,
                  val properties: MixitProperties) {

    fun findByEventView(year: Int, req: ServerRequest, topic: String? = null) = ok().render("talks", mapOf(
            Pair("talks", repository
                    .findByEvent(year.toString(), topic)
                    .collectList()
                    .flatMap { talks ->
                        userRepository
                                .findMany(talks.flatMap(Talk::speakerIds))
                                .collectMap(User::login)
                                .map { speakers -> talks.map { it.toDto(req.language(), it.speakerIds.mapNotNull { speakers[it] }, markdownConverter) } }
                    }),
            Pair("year", year),
            Pair("title", when (topic) { null -> "talks.title.html|$year"
                else -> "talks.title.html.$topic|$year"
            }),
            Pair("baseUri", UriUtils.encode(properties.baseUri, StandardCharsets.UTF_8)),
            Pair("topic", topic)
    ))


    fun findOneView(year: Int, req: ServerRequest) = repository.findByEventAndSlug(year.toString(), req.pathVariable("slug")).flatMap { talk ->
        userRepository.findMany(talk.speakerIds).collectList().flatMap { speakers ->
            ok().render("talk", mapOf(
                    Pair("talk", talk.toDto(req.language(), speakers!!, markdownConverter)),
                    Pair("speakers", speakers.map { it.toDto(req.language(), markdownConverter) }),
                    Pair("title", "talk.html.title|${talk.title}"),
                    Pair("baseUri", UriUtils.encode(properties.baseUri, StandardCharsets.UTF_8))))
        }
    }

    fun findOne(req: ServerRequest) = ok().json().body(repository.findOne(req.pathVariable("login")))

    fun findByEventId(req: ServerRequest) =
            ok().json().body(repository.findByEvent(req.pathVariable("year")))

    fun redirectFromId(req: ServerRequest) = repository.findOne(req.pathVariable("id")).flatMap {
        permanentRedirect("${properties.baseUri}/${it.event}/${it.slug}")
    }

    fun redirectFromSlug(req: ServerRequest) = repository.findBySlug(req.pathVariable("slug")).flatMap {
        permanentRedirect("${properties.baseUri}/${it.event}/${it.slug}")
    }

    fun planning(req: ServerRequest) = ok().render("planning", mapOf(
            Pair("talks", repository
                    .findByEvent("2017", null)
                    .collectList()
                    .flatMap { talks -> createTalkMapByDateAndRoom(talks, req.language()).toMono()}),

            Pair("baseUri", UriUtils.encode(properties.baseUri, StandardCharsets.UTF_8))
    ))

    private fun createTalkMapByDateAndRoom(talks: List<Talk>, lang: Language): Map<String, Map<String, List<TalkDto>>> = talks
            .map { it.start!!.toLocalDate() }
            .distinct()
            .map { createTalksByDate(it, talks) }
            .associateBy({ "J${it.date.dayOfMonth}" }, { it.talks })
            .entries
            .associateBy({ it.key }, { createTalksByRoom(it.value, lang) })

    private fun createTalksByRoom(talksByDate: List<Talk>, lang: Language): Map<String, List<TalkDto>> =
            listOf(Room.AMPHI1, Room.AMPHI2, Room.ROOM1, Room.ROOM2, Room.ROOM3, Room.ROOM4, Room.ROOM5, Room.ROOM6)
                    .stream()
                    .map { createTalksByRoom(it, talksByDate) }
                    .toList()
                    .associateBy({ it.room.name }, { it.talks.map {
                        talk -> userRepository.findMany(talk.speakerIds)
                            .collectMap(User::login)
                            .map { speakers -> talk.toDto(lang, talk.speakerIds.mapNotNull { speakers[it] }, markdownConverter) }
                            .block()
                    } })

    private fun createTalksByDate(date: LocalDate, talks: List<Talk>) = TalkByDateDto(
            date,
            talks.filter { it.start!!.toLocalDate().equals(date) }
    )

    private fun createTalksByRoom(room: Room, talks: List<Talk>) = TalkByRoomDto(
            room,
            talks.filter { it.room == room }.sortedBy { it.start }
    )

}

class TalkByDateDto(
        val date:LocalDate,
        val talks: List<Talk>
)

class TalkByRoomDto(
        val room:Room,
        val talks: List<Talk>
)

class TalkDto(
        val id: String?,
        val slug: String,
        val format: TalkFormat,
        val event: String,
        val title: String,
        val summary: String,
        val speakers: List<User>,
        val language: String,
        val addedAt: LocalDateTime,
        val description: String?,
        val topic: String?,
        val video: String?,
        val room: String?,
        val start: String?,
        val end: String?,
        val date: String?
)

fun Talk.toDto(lang: Language, speakers: List<User>, markdownConverter: MarkdownConverter) = TalkDto(
        id, slug, format, event, title,
        markdownConverter.toHTML(summary), speakers, language.name.toLowerCase(), addedAt,
        markdownConverter.toHTML(description), topic,
        video, "rooms.${room?.name?.toLowerCase()}" , start?.formatTalkTime(lang), end?.formatTalkTime(lang),
        start?.formatTalkDate(lang)
)
