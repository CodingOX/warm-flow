package org.dromara.warm.demo.definition;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.ApiResult;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.utils.page.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo 侧已发布流程定义查询和发起接口。
 * <p>
 * 注意：Long 类型的 id 序列化为 JSON 时会超出 JS Number 安全范围，
 * 因此 {@link DefinitionView#id} 使用 String 类型。
 *
 * @author warm-flow-demo
 */
@RestController
@RequestMapping("/demo/definitions")
public class DemoFlowDefinitionController {

    /**
     * 查询已发布的流程定义列表（分页）
     */
    @GetMapping("/published")
    public ApiResult<Page<DefinitionView>> published() {
        Definition query = FlowEngine.newDef().setIsPublish(PublishStatus.PUBLISHED.getKey());
        Page<Definition> pageParam = new Page<>(1, 50);
        Page<Definition> page = FlowEngine.defService().page(query, pageParam);

        // 将 Definition 转成 DefinitionView，id 作为字符串传给前端，避免 JS 精度丢失
        List<DefinitionView> views = page.getList().stream()
                .map(DefinitionView::from)
                .collect(Collectors.toList());
        Page<DefinitionView> result = new Page<>(views, page.getTotal());
        return ApiResult.ok(result);
    }

    /**
     * 发起流程定义，返回第一个待办任务信息。
     * <p>
     * defId 使用 String 接收，手动转为 Long，避免前端精度丢失传错 ID。
     *
     * @param defId 流程定义ID（字符串）
     * @return { instanceId, taskId } 均为字符串
     */
    @PostMapping("/start/{defId}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, String>> start(@PathVariable String defId) {
        // 1. 查出已发布的流程定义
        Definition def = FlowEngine.defService().getById(Long.valueOf(defId));
        if (def == null || !PublishStatus.PUBLISHED.getKey().equals(def.getIsPublish())) {
            return ApiResult.fail("流程定义未发布或不存在");
        }

        // 2. 发起流程
        String businessId = "biz_" + System.currentTimeMillis();
        FlowParams flowParams = FlowParams.build()
                .flowCode(def.getFlowCode())
                .handler("applicant");
        Instance instance = FlowEngine.insService().start(businessId, flowParams);

        // 3. 查找第一个待办任务
        Task queryTask = FlowEngine.newTask();
        queryTask.setInstanceId(instance.getId());
        List<Task> tasks = FlowEngine.taskService().list(queryTask);
        if (tasks.isEmpty()) {
            return ApiResult.fail("流程已发起但未产生待办任务");
        }
        Task firstTask = tasks.get(0);

        // 4. 返回 instanceId 和 taskId（均转为字符串，避免 JS 精度丢失）
        Map<String, String> result = new LinkedHashMap<>();
        result.put("instanceId", String.valueOf(instance.getId()));
        result.put("taskId", String.valueOf(firstTask.getId()));
        return ApiResult.ok(result);
    }

    /**
     * 给前端展示的流程定义视图，id 使用 String 防止 JS 精度丢失。
     */
    public static class DefinitionView {
        private String id;
        private String flowCode;
        private String flowName;
        private String version;
        private String category;
        private Date createTime;

        public static DefinitionView from(Definition def) {
            DefinitionView v = new DefinitionView();
            v.id = String.valueOf(def.getId());
            v.flowCode = def.getFlowCode();
            v.flowName = def.getFlowName();
            v.version = def.getVersion();
            v.category = def.getCategory();
            v.createTime = def.getCreateTime();
            return v;
        }

        public String getId() { return id; }
        public String getFlowCode() { return flowCode; }
        public String getFlowName() { return flowName; }
        public String getVersion() { return version; }
        public String getCategory() { return category; }
        public Date getCreateTime() { return createTime; }
    }
}
