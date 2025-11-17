package io.ktor.samples.openapi

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun Application.configureRouting() {
    routing {
        authenticate("auth-oauth-google") {
            /**
             * Authenticate.
             */
            get("/login") {
                // When user hits this endpoint, Ktor automatically redirects to Google OAuth
                // After successful login at Google, they'll be redirected to /callback
            }

            /**
             * Oauth callback endpoint.
             */
            get("/callback") {
                // Google redirects here after authentication
                // The OAuth plugin automatically handles the token exchange
                val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                if (principal != null) {
                    // Fetch user info from Google
                    val httpClient = HttpClient(Apache)
                    try {
                        val userInfoResponse = httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
                            header(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
                        }.body<String>()
                        
                        // Parse user info from the response
                        val json = Json { ignoreUnknownKeys = true }
                        val userInfo = json.decodeFromString<UserInfo>(userInfoResponse)
                        
                        // Log all user information from Google
                        application.log.info("=== Google OAuth User Information ===")
                        application.log.info("User ID: ${userInfo.id}")
                        application.log.info("Email: ${userInfo.email ?: "N/A"}")
                        application.log.info("Verified Email: ${userInfo.verified_email ?: "N/A"}")
                        application.log.info("Name: ${userInfo.name ?: "N/A"}")
                        application.log.info("Given Name: ${userInfo.given_name ?: "N/A"}")
                        application.log.info("Family Name: ${userInfo.family_name ?: "N/A"}")
                        application.log.info("Picture URL: ${userInfo.picture ?: "N/A"}")
                        application.log.info("Locale: ${userInfo.locale ?: "N/A"}")
                        application.log.info("Access Token: ${principal.accessToken.take(20)}...")
                        application.log.info("=====================================")
                        
                        // Store user information in the session
                        call.sessions.set(UserSession(
                            accessToken = principal.accessToken,
                            userId = userInfo.id,
                            email = userInfo.email,
                            name = userInfo.name,
                            verifiedEmail = userInfo.verified_email,
                            givenName = userInfo.given_name,
                            familyName = userInfo.family_name,
                            picture = userInfo.picture,
                            locale = userInfo.locale
                        ))
                        call.respondRedirect("/hello")
                    } catch (e: Exception) {
                        call.respondText("Failed to fetch user info: ${e.message}", status = HttpStatusCode.InternalServerError)
                    } finally {
                        httpClient.close()
                    }
                } else {
                    call.respondText("Authentication failed", status = HttpStatusCode.Unauthorized)
                }
            }
        }
        
        /**
         * Hello, world.
         *
         * @response 200 text/plaintext Hello
         */
        get("/hello") {
            val userSession = call.sessions.get<UserSession>()
            if (userSession != null) {
                val response = buildString {
                    appendLine("Hello! You are authenticated.")
                    appendLine()
                    appendLine("=== User Information ===")
                    appendLine("User ID: ${userSession.userId}")
                    appendLine("Email: ${userSession.email ?: "N/A"}")
                    appendLine("Verified Email: ${userSession.verifiedEmail ?: "N/A"}")
                    appendLine("Name: ${userSession.name ?: "N/A"}")
                    appendLine("Given Name: ${userSession.givenName ?: "N/A"}")
                    appendLine("Family Name: ${userSession.familyName ?: "N/A"}")
                    appendLine("Picture URL: ${userSession.picture ?: "N/A"}")
                    appendLine("Locale: ${userSession.locale ?: "N/A"}")
                    appendLine("========================")
                }
                call.respondText(response)
            } else {
                call.respondRedirect("/login")
            }
        }
        
        // Store users list at application level
        val usersList = mutableListOf<User>()
        
        /**
         * Data back-end.
         */
        route("/data") {

            /**
             * Users endpoint.
             */
                route("/users") {

                    /**
                     * Get a single user by ID.
                     *
                     * @path id [ULong] the ID of the user
                     * @response 400 The ID parameter is malformatted or missing.
                     * @response 404 The user for the given ID does not exist.
                     * @response 200 The user found with the given ID.
                     */
                    get("/{id}") {
                        val userSession = call.sessions.get<UserSession>()
                        if (userSession == null) {
                            return@get call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
                        }
                        
                        val id = call.parameters["id"]?.toULongOrNull()
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val user = usersList.find { it.id == id }
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                        call.respond(user)
                    }
                    /**
                     * Get a list of users.
                     *
                     * @response 200 The list of items.
                     */
                    get {
                        val userSession = call.sessions.get<UserSession>()
                        if (userSession == null) {
                            return@get call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
                        }
                        call.respond<List<User>>(usersList)
                    }

                    /**
                     * Save a new user.
                     *
                     * @response 204 The new user was saved.
                     */
                    post {
                        val userSession = call.sessions.get<UserSession>()
                        if (userSession == null) {
                            return@post call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
                        }
                        
                        usersList += call.receive<User>()
                        call.respond(HttpStatusCode.NoContent)
                    }
                    /**
                     * Delete the user with the given ID.
                     *
                     * @path id [ULong] the ID of the user to remove
                     * @response 400 The ID parameter is malformatted or missing.
                     * @response 404 The user for the given ID does not exist.
                     * @response 204 The user was deleted.
                     */
                    delete("/{id}") {
                        val userSession = call.sessions.get<UserSession>()
                        if (userSession == null) {
                            return@delete call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
                        }
                        
                        val id = call.parameters["id"]?.toULongOrNull()
                            ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        if (!usersList.removeIf { it.id == id })
                            return@delete call.respond(HttpStatusCode.NotFound)
                        call.respond(HttpStatusCode.NoContent)
                    }
            }
        }
    }
}

@Serializable
data class User(val id: ULong, val name: String)

@Serializable
data class UserInfo(
    val id: String,
    val email: String? = null,
    val verified_email: Boolean? = null,
    val name: String? = null,
    val given_name: String? = null,
    val family_name: String? = null,
    val picture: String? = null,
    val locale: String? = null
)