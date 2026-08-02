package org.darkroomlibrary.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.rate-limit.ingress-max-per-minute=100",
        "security.rate-limit.public-file-max-per-minute=3",
        "security.rate-limit.anonymous-max-per-minute=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InterceptorConfigPathTest {

    private static final String CONTEXT_PATH = "/api/test";
    private static final String PUBLIC_FILE_PATH =
            CONTEXT_PATH + "/file/public?fileName=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicFilesUseTheirDedicatedServletRelativeLimit() throws Exception {
        mockMvc.perform(get(PUBLIC_FILE_PATH).contextPath(CONTEXT_PATH))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PUBLIC_FILE_PATH).contextPath(CONTEXT_PATH))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PUBLIC_FILE_PATH).contextPath(CONTEXT_PATH))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PUBLIC_FILE_PATH).contextPath(CONTEXT_PATH))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void publicHealthProbeIsExcludedFromSubjectLimit() throws Exception {
        mockMvc.perform(get(CONTEXT_PATH + "/health/live").contextPath(CONTEXT_PATH))
                .andExpect(status().isOk());
        mockMvc.perform(get(CONTEXT_PATH + "/health/live").contextPath(CONTEXT_PATH))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightAllowsRequestCorrelationHeader() throws Exception {
        mockMvc.perform(options(CONTEXT_PATH + "/health/live")
                        .contextPath(CONTEXT_PATH)
                        .header("Origin", "http://localhost:5175")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Request-ID"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        containsStringIgnoringCase("X-Request-ID")));
    }
}
