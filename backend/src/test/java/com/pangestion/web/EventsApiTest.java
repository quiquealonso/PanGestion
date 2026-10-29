package com.pangestion.web;

import com.pangestion.events.LiveEvent;
import com.pangestion.events.LiveEventsHub;
import com.pangestion.model.Company;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Avisos en tiempo real: /api/events (Server-Sent Events). */
@DisplayName("API · Avisos en tiempo real")
class EventsApiTest extends ApiTestSupport {

    @Autowired
    private LiveEventsHub hub;

    private MvcResult connect(User user) throws Exception {
        return mvc.perform(get("/api/events").accept(MediaType.TEXT_EVENT_STREAM).with(as(user)))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    @Test
    @DisplayName("la conexión queda abierta y recibe los avisos de su empresa, no los de otra")
    void receivesOwnCompanyEvents() throws Exception {
        Company bar = company("Bar");
        Company other = company("Otro");
        MvcResult result = connect(user(bar, Role.CAMARERO));

        hub.broadcast(LiveEvent.ready(bar.getId(), new LiveEvent.ReadyData(7L, "4 + 5", "Carla", List.of("2× Burger"))));
        hub.broadcast(LiveEvent.change(other.getId(), "kitchen"));

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8); // SSE siempre va en UTF-8
        assertThat(body).contains("event:hello");
        assertThat(body).contains("event:ready").contains("\"tableLabel\":\"4 + 5\"").contains("2× Burger");
        assertThat(body).doesNotContain("event:change");
    }

    @Test
    @DisplayName("sin token: 401")
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/events").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
    }
}
