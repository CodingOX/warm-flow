package org.dromara.warm.demo.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoGodWorkbenchSubmitController.class)
class DemoGodWorkbenchSubmitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DemoGodWorkbenchSubmitController.GodWorkbenchSubmitFacade submitFacade;

    @Test
    void submit_shouldRejectBlankTaskId() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", " ");
        body.put("message", "同意");
        body.put("formData", Map.of("leaveDays", 5));

        mockMvc.perform(post("/demo/god-workbench/submit")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submit_shouldReturnNextRuntimeState() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", "188800020004");
        body.put("message", "同意，流程配置验证通过");
        body.put("formData", Map.of("leaveDays", 5, "reason", "测试"));

        given(submitFacade.submit(any()))
            .willReturn(new DemoGodWorkbenchSubmitController.GodSubmitResult(
                "188800010001",
                "188800020005",
                "manager_approve",
                "RUNNING",
                false
            ));

        mockMvc.perform(post("/demo/god-workbench/submit")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.instanceId").value("188800010001"))
            .andExpect(jsonPath("$.data.taskId").value("188800020005"))
            .andExpect(jsonPath("$.data.currentNodeCode").value("manager_approve"))
            .andExpect(jsonPath("$.data.flowStatus").value("RUNNING"))
            .andExpect(jsonPath("$.data.ended").value(false));
    }

    @Test
    void history_shouldReturnHistoryEntries() throws Exception {
        given(submitFacade.history("188800010001"))
            .willReturn(List.of(
                new DemoGodWorkbenchSubmitController.GodHistoryItem(
                    "188800030001",
                    "start_apply",
                    "发起申请",
                    "PASS",
                    "申请人提交",
                    new Date(1718548800000L)
                ),
                new DemoGodWorkbenchSubmitController.GodHistoryItem(
                    "188800030002",
                    "leader_approve",
                    "主管审批",
                    "PASS",
                    "同意",
                    new Date(1718552400000L)
                )
            ));

        mockMvc.perform(get("/demo/god-workbench/history/188800010001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].hisTaskId").value("188800030001"))
            .andExpect(jsonPath("$.data[0].nodeCode").value("start_apply"))
            .andExpect(jsonPath("$.data[0].nodeName").value("发起申请"))
            .andExpect(jsonPath("$.data[1].hisTaskId").value("188800030002"))
            .andExpect(jsonPath("$.data[1].message").value("同意"));
    }
}
