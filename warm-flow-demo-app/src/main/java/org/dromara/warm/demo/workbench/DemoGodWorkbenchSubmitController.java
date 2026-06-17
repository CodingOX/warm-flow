package org.dromara.warm.demo.workbench;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.ApiResult;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * demo 专用上帝模式提交流程接口。
 */
@RestController
@RequestMapping("/demo/god-workbench")
public class DemoGodWorkbenchSubmitController {

    private final GodWorkbenchSubmitFacade submitFacade;

    public DemoGodWorkbenchSubmitController(GodWorkbenchSubmitFacade submitFacade) {
        this.submitFacade = submitFacade;
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResult<Map<String, Object>>> submit(@RequestBody GodSubmitRequest request) {
        if (request == null || request.getTaskId() == null || request.getTaskId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResult.fail(400, "taskId不能为空"));
        }
        GodSubmitResult result = submitFacade.submit(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instanceId", result.instanceId());
        body.put("taskId", result.taskId());
        body.put("currentNodeCode", result.currentNodeCode());
        body.put("flowStatus", result.flowStatus());
        body.put("ended", result.ended());
        return ResponseEntity.ok(ApiResult.ok(body));
    }

    @GetMapping("/history/{instanceId}")
    public ResponseEntity<ApiResult<List<GodHistoryItem>>> history(@PathVariable String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResult.fail(400, "instanceId不能为空"));
        }
        return ResponseEntity.ok(ApiResult.ok(submitFacade.history(instanceId)));
    }

    public record GodSubmitResult(
        String instanceId,
        String taskId,
        String currentNodeCode,
        String flowStatus,
        boolean ended
    ) {
    }

    public record GodHistoryItem(
        String hisTaskId,
        String nodeCode,
        String nodeName,
        String skipType,
        String message,
        Date createTime
    ) {
    }

    /**
     * 这里故意留在 demo 模块内部：
     * 1. 第一版只验证 ignore(true) 的真实闭环；
     * 2. 不把 demo 演练逻辑扩散进核心引擎或通用 UI 层。
     */
    @Component
    static class GodWorkbenchSubmitFacade {

        GodSubmitResult submit(GodSubmitRequest request) {
            Long taskId = Long.valueOf(request.getTaskId());
            FlowParams flowParams = FlowParams.build()
                .skipType("PASS")
                .message(request.getMessage())
                // 与现有 UI 办理链路保持一致：业务表单值挂在 variable.formData 下。
                .formData(request.getFormData())
                .ignore(true);

            Instance instance = FlowEngine.taskService().skip(taskId, flowParams);

            Task query = FlowEngine.newTask();
            query.setInstanceId(instance.getId());
            List<Task> tasks = FlowEngine.taskService().list(query);
            Task nextTask = tasks.isEmpty() ? null : tasks.get(0);

            return new GodSubmitResult(
                String.valueOf(instance.getId()),
                nextTask == null ? "" : String.valueOf(nextTask.getId()),
                nextTask == null ? "" : nextTask.getNodeCode(),
                nextTask == null ? "FINISH" : nextTask.getFlowStatus(),
                nextTask == null
            );
        }

        List<GodHistoryItem> history(String instanceIdText) {
            Long instanceId = Long.valueOf(instanceIdText);
            return FlowEngine.hisTaskService().getByInsId(instanceId).stream()
                .sorted(Comparator.comparing(HisTask::getCreateTime, Comparator.nullsLast(Date::compareTo))
                    .thenComparing(HisTask::getId, Comparator.nullsLast(Long::compareTo)))
                .map(hisTask -> new GodHistoryItem(
                    String.valueOf(hisTask.getId()),
                    hisTask.getNodeCode(),
                    hisTask.getNodeName(),
                    hisTask.getSkipType(),
                    hisTask.getMessage(),
                    hisTask.getCreateTime()
                ))
                .collect(Collectors.toList());
        }
    }
}
