package io.ktor.samples.openapi

import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureOpenApi() {
    routing {
        // Serve the OpenAPI spec as JSON
        openAPI(
            path = "openapi",
            swaggerFile = "openapi/generated.json"
        )
        
        // Serve Swagger UI for interactive API documentation
        swaggerUI(
            path = "swagger",
            swaggerFile = "openapi/generated.json"
        ) {
            // Disable OAuth in Swagger UI - users should authenticate via /login first
            // Then the session cookie will be used for all Swagger UI requests
            version = "5.10.5"
        }
    }
}
