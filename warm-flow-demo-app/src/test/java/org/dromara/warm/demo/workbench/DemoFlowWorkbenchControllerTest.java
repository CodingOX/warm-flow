package org.dromara.warm.demo.workbench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoFlowWorkbenchController.class)
class DemoFlowWorkbenchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void workbenchPage_shouldBeServed() throws Exception {
        mockMvc.perform(get("/demo/workbench"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/demo-workbench.html"));
    }
}
