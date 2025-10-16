package com.rpo.mimico.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .components(securityComponents())
                .addSecurityItem(securityRequirement());
    }

    private Info apiInfo() {
        return new Info()
                .title("Mímico API")
                .version("v1")
                .description("""
                        API for the Mímico multiplayer game.
                        
                        ## Game Overview
                        4-player online charades game where teams compete by guessing words based on mime performances.
                        
                        ## WebSocket Topics
                        
                        **Subscribe to:**
                        - `/topic/lobby/users` - Online users updates
                        - `/topic/table/{tableId}/player-accepted` - Player joins
                        - `/topic/table/{tableId}/status` - Table status
                        - `/topic/table/{tableId}/match-started` - Match begins
                        - `/topic/match/{matchId}/paused` - Match paused
                        - `/topic/match/{matchId}/resumed` - Match resumed
                        - `/topic/match/{matchId}/timeout` - Round timeout
                        - `/topic/match/{matchId}/ended` - Match finished
                        - `/user/queue/invite` - Personal invites
                        - `/user/queue/errors` - Error messages
                        - `/user/queue/game-state` - Game state restoration
                        
                        **Send to:**
                        - `/app/table/invite` - Send invite
                        - `/app/table/invite/accept` - Accept invite
                        - `/app/table/invite/reject` - Reject invite
                        - `/app/match/{matchId}/chat` - Send chat/guess
                        
                        ## Authentication
                        All endpoints require JWT Bearer token (except login/register).
                        """)
                .contact(new Contact()
                        .name("Mímico Team"))
                .license(new License()
                        .name("MIT")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> servers() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Local development"),
                new Server()
                        .url("https://mimico-backend.onrender.com")
                        .description("Production")
        );
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token from /auth/login"));
    }

    private SecurityRequirement securityRequirement() {
        return new SecurityRequirement().addList("bearer-jwt");
    }
}