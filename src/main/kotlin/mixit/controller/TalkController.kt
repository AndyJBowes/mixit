package mixit.controller

import mixit.model.*
import mixit.repository.EventRepository
import mixit.repository.TalkRepository
import mixit.support.RouterFunctionProvider
import mixit.support.MarkdownConverter
import mixit.support.json
import org.springframework.http.MediaType.*
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.ServerResponse.*
import java.time.LocalDateTime


class TalkController(val repository: TalkRepository,
                     val eventRepository: EventRepository,
                     val markdownConverter: MarkdownConverter): RouterFunctionProvider() {

    // TODO Remove this@TalkController when KT-15667 will be fixed
    override val routes: Routes = {
        accept(TEXT_HTML).route {
            GET("/2017") { ok().render("talks-2017") }
            GET("/2016") { findByEventView(2016, it) }
            GET("/2015") { findByEventView(2015, it) }
            GET("/2014") { findByEventView(2014, it) }
            GET("/2013") { findByEventView(2013, it) }
            GET("/2012") { findByEventView(2012, it) }
            GET("/talk/{slug}", this@TalkController::findOneView)
        }
        (accept(APPLICATION_JSON) and "/api").route {
            GET("/talk/{login}", this@TalkController::findOne)
            GET("/{year}/talk", this@TalkController::findByEventId)
        }
    }

    fun findByEventView(year: Int, req: ServerRequest) =
            repository.findByEvent(eventRepository.yearToId(year.toString())).collectList().then { sessions ->
                val model = mapOf(Pair("talks", sessions.map { SessionDto(it, markdownConverter) }), Pair("year", year))
                ok().render("talks", model)
            }

    fun findOneView(req: ServerRequest) = repository.findBySlug(req.pathVariable("slug")).then { s ->
        ok().render("talk", mapOf(Pair("talk", SessionDto(s, markdownConverter))))
    }

    fun findOne(req: ServerRequest) = ok().json().body(repository.findOne(req.pathVariable("login")))

    fun findByEventId(req: ServerRequest) =
            ok().json().body(repository.findByEvent(eventRepository.yearToId(req.pathVariable("year"))))


    class SessionDto(
            val id: String?,
            val slug: String,
            val format: SessionFormat,
            val event: String,
            val title: String,
            val summary: String,
            val speakers: List<User>,
            val language: Language,
            val addedAt: LocalDateTime,
            val description: String?,
            val video: String?,
            val room: Room?,
            val start: LocalDateTime?,
            val end: LocalDateTime?
    ) {

        constructor(talk: Talk, markdownConverter: MarkdownConverter) : this(talk.id, talk.slug, talk.format, talk.event,
                talk.title, markdownConverter.toHTML(talk.summary), talk.speakers, talk.language, talk.addedAt,
                markdownConverter.toHTML(talk.description), talk.video, talk.room, talk.start, talk.end)

    }
}
