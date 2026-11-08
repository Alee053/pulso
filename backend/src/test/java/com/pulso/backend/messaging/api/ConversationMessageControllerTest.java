package com.pulso.backend.messaging.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pulso.backend.config.MessageProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.messaging.application.InMemoryConversationMessageRepository;
import com.pulso.backend.messaging.application.ListConversationMessages;
import com.pulso.backend.messaging.application.SendTextMessage;
import com.pulso.backend.messaging.domain.Message;
import com.pulso.backend.messaging.domain.MessageStatus;
import com.pulso.backend.messaging.domain.MessageType;

class ConversationMessageControllerTest {

    private static final UUID CONVERSATION_ID = UUID.fromString("28444444-4444-4444-8444-444444444444");
    private static final UUID SENDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECIPIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OUTSIDER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final MessageProperties PROPERTIES = new MessageProperties(0, 2, 3, 10, 20);

    private final InMemoryConversationMessageRepository repository =
            new InMemoryConversationMessageRepository(CONVERSATION_ID, SENDER_ID, RECIPIENT_ID);

    @Test
    void sendsATextMessageUsingOnlyTheJwtIdentity() throws Exception {
        mockMvc(SENDER_ID.toString()).perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"  Hola equipo  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.senderProfileId").value(SENDER_ID.toString()))
                .andExpect(jsonPath("$.messageType").value("TEXT"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.body").value("Hola equipo"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.editedAt").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist());

        assertThat(repository.messages).hasSize(1);
    }

    @Test
    void rejectsClientControlledSenderTypeAndStatus() throws Exception {
        for (String field : List.of("senderProfileId", "messageType", "status", "createdAt")) {
            mockMvc(SENDER_ID.toString()).perform(post(path())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\":\"Hola\",\"" + field + "\":\"" + RECIPIENT_ID + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"));
        }

        assertThat(repository.messages).isEmpty();
    }

    @Test
    void rejectsMissingBlankAndOversizedBodies() throws Exception {
        mockMvc(SENDER_ID.toString()).perform(post(path()))
                .andExpect(status().isBadRequest());
        mockMvc(SENDER_ID.toString()).perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("body is required."));
        mockMvc(SENDER_ID.toString()).perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("body is required."));
        mockMvc(SENDER_ID.toString()).perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + "x".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("body must not exceed 20 characters."));

        assertThat(repository.messages).isEmpty();
    }

    @Test
    void externalUsersCannotSendOrRead() throws Exception {
        repository.appendText(CONVERSATION_ID, SENDER_ID, "privado");

        mockMvc(OUTSIDER_ID.toString()).perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hola\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("conversation_not_found"));
        mockMvc(OUTSIDER_ID.toString()).perform(get(path()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("conversation_not_found"));

        assertThat(repository.messages).hasSize(1);
    }

    @Test
    void mapsInactiveConversationsAndBlocksToProblems() throws Exception {
        repository.conversationStatus = "CLOSED";
        mockMvc(SENDER_ID.toString()).perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hola\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conversation_not_active"));

        repository.conversationStatus = "ACTIVE";
        repository.blocked = true;
        mockMvc(SENDER_ID.toString()).perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hola\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("messaging_blocked"));
    }

    @Test
    void listsMessagesOldestFirstWithPaginationMetadata() throws Exception {
        repository.appendText(CONVERSATION_ID, SENDER_ID, "uno");
        repository.appendText(CONVERSATION_ID, RECIPIENT_ID, "dos");
        repository.appendText(CONVERSATION_ID, SENDER_ID, "tres");

        mockMvc(RECIPIENT_ID.toString()).perform(get(path()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].body").value("uno"))
                .andExpect(jsonPath("$.data[1].body").value("dos"))
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.hasMore").value(true));

        mockMvc(RECIPIENT_ID.toString()).perform(get(path()).param("offset", "2").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].body").value("tres"))
                .andExpect(jsonPath("$.meta.offset").value(2))
                .andExpect(jsonPath("$.meta.limit").value(3))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
    }

    @Test
    void deletedMessagesAreListedWithoutTheirBody() throws Exception {
        OffsetDateTime deletedAt = OffsetDateTime.parse("2026-11-07T16:00:00Z");
        repository.messages.add(new Message(
                UUID.randomUUID(), CONVERSATION_ID, SENDER_ID, MessageType.TEXT,
                MessageStatus.DELETED, "texto retirado",
                OffsetDateTime.parse("2026-11-07T15:00:00Z"), null, deletedAt));

        mockMvc(RECIPIENT_ID.toString()).perform(get(path()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("DELETED"))
                .andExpect(jsonPath("$.data[0].body").doesNotExist())
                .andExpect(jsonPath("$.data[0].deletedAt").exists());
    }

    @Test
    void rejectsPageWindowsOutsideTheConfiguredLimits() throws Exception {
        mockMvc(SENDER_ID.toString()).perform(get(path()).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("limit must be between 1 and 3."));
        mockMvc(SENDER_ID.toString()).perform(get(path()).param("limit", "4"))
                .andExpect(status().isBadRequest());
        mockMvc(SENDER_ID.toString()).perform(get(path()).param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("offset must be between 0 and 10."));
        mockMvc(SENDER_ID.toString()).perform(get(path()).param("offset", "11"))
                .andExpect(status().isBadRequest());
        mockMvc(SENDER_ID.toString()).perform(get(path()).param("limit", "many"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnInvalidAuthenticatedSubject() throws Exception {
        mockMvc("not-a-uuid").perform(get(path()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mockMvc("1-1-1-1-1")
                .perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hola\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static String path() {
        return "/api/v1/conversations/" + CONVERSATION_ID + "/messages";
    }

    private MockMvc mockMvc(String subject) {
        Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(subject)
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
        return MockMvcBuilders
                .standaloneSetup(new ConversationMessageController(
                        new SendTextMessage(repository, PROPERTIES),
                        new ListConversationMessages(repository),
                        PROPERTIES))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver(jwt))
                .build();
    }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {

        private final Jwt jwt;

        private JwtArgumentResolver(Jwt jwt) {
            this.jwt = jwt;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return jwt;
        }
    }
}
