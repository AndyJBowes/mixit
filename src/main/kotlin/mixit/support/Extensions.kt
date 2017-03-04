package mixit.support

import com.mongodb.client.result.DeleteResult
import mixit.model.Language
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.core.io.support.ResourcePropertySource
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType.*
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.*
import kotlin.reflect.KClass

inline fun <reified T : Any> ReactiveMongoOperations.findById(id: Any) : Mono<T> = findById(id, T::class.java)

fun <T : Any> ReactiveMongoOperations.findById(id: Any, type: KClass<T>) : Mono<T> = findById(id, type.java)

inline fun <reified T : Any> ReactiveMongoOperations.find(query: Query) : Flux<T> = find(query, T::class.java)

fun <T : Any> ReactiveMongoOperations.findAll(type: KClass<T>) : Flux<T> = findAll(type.java)

fun <T : Any> ReactiveMongoOperations.find(query: Query, type: KClass<T>) : Flux<T> = find(query, type.java)

inline fun <reified T : Any> ReactiveMongoOperations.findOne(query: Query) : Mono<T> = find(query, T::class.java).next()

fun ReactiveMongoOperations.remove(query: Query, type: KClass<*>): Mono<DeleteResult> = remove(query, type.java)

fun ServerRequest.language() = Language.findByTag(this.headers().header(HttpHeaders.ACCEPT_LANGUAGE).first())

fun String.stripAccents() = Normalizer.normalize(this, Normalizer.Form.NFD).replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

fun String.toSlug() = this.toLowerCase()
            .stripAccents()
            .replace("\n", " ")
            .replace("[^a-z\\d\\s]".toRegex(), " ")
            .split(" ")
            .joinToString("-")

fun <T> Iterable<T>.shuffle(): Iterable<T> {
    val shuffledList = this.toMutableList()
    Collections.shuffle(shuffledList)
    return shuffledList
}

fun ServerResponse.BodyBuilder.json() = contentType(APPLICATION_JSON_UTF8)

fun ServerResponse.BodyBuilder.xml() = contentType(APPLICATION_XML)

fun ServerResponse.BodyBuilder.html() = contentType(TEXT_HTML)

fun permanentRedirect(uri: String) = ServerResponse.permanentRedirect(URI(uri)).build()

fun ConfigurableEnvironment.addPropertySource(location: String) {
    propertySources.addFirst(ResourcePropertySource(EncodedResource(ClassPathResource(location), StandardCharsets.UTF_8)))
}
