package com.myprueba;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myprueba.support.ApiTest;
import org.junit.jupiter.api.Test;

class OpenApiTest extends ApiTest {

    @Test
    void apiContractIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/sales']").exists())
                .andExpect(jsonPath("$.paths['/api/balance']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register']").exists());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
