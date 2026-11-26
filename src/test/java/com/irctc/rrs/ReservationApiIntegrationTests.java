package com.irctc.rrs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationApiIntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void apiServesTrainsAndProcessesIdempotentBookingEndToEnd() throws Exception {
        mockMvc.perform(get("/api/v1/trains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value("12951"));

        String payload = """
                {
                  "userId": "api-integration-user",
                  "trainNumber": "12621",
                  "sourceStation": "MAS",
                  "destinationStation": "NDLS",
                  "coachClass": "AC_3",
                  "quotaCode": "GN",
                  "paymentMode": "NORMAL_SUCCESS",
                  "bypassTatkalClock": true,
                  "passengers": [
                    {"name": "Karthik R", "age": 27, "gender": "MALE", "preference": "LOWER"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "api-test-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "api-test-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
