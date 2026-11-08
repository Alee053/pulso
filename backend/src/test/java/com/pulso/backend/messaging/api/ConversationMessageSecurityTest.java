package com.pulso.backend.messaging.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.MessageProperties;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.messaging.application.ConversationMessageRepository;
import com.pulso.backend.messaging.application.InMemoryConversationMessageRepository;
import com.pulso.backend.messaging.application.ListConversationMessages;
import com.pulso.backend.messaging.application.SendTextMessage;

@WebMvcTest(ConversationMessageController.class)
@Import({SecurityConfig.class, ConversationMessageSecurityTest.TestBeans.class})
class ConversationMessageSecurityTest {

    private static final UUID CONVERSATION_ID = UUID.fromString("28444444-4444-4444-8444-444444444444");
    private static final UUID PARTICIPANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessTokenToSendAndToRead() throws Exception {
        mockMvc.perform(post(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hola\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(path())).andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedParticipantToSendAndToRead() throws Exception {
        mockMvc.perform(post(path())
                        .with(jwt().jwt(accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hola\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get(path()).with(jwt().jwt(accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    void doesNotExposeEditingOrDeletingYet() throws Exception {
        mockMvc.perform(patch(path() + "/" + UUID.randomUUID()).with(jwt().jwt(accessToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path() + "/" + UUID.randomUUID()).with(jwt().jwt(accessToken())))
                .andExpect(status().isForbidden());
    }

    private static Jwt accessToken() {
        return Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(PARTICIPANT_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
    }

    private static String path() {
        return "/api/v1/conversations/" + CONVERSATION_ID + "/messages";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        ConversationMessageRepository conversationMessageRepository() {
            return new InMemoryConversationMessageRepository(CONVERSATION_ID, PARTICIPANT_ID);
        }

        @Bean
        SendTextMessage sendTextMessage(
                ConversationMessageRepository repository,
                MessageProperties properties) {
            return new SendTextMessage(repository, properties);
        }

        @Bean
        ListConversationMessages listConversationMessages(ConversationMessageRepository repository) {
            return new ListConversationMessages(repository);
        }
    }
}
