package org.dromara.warm.demo.workbench;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上帝模式提交请求。
 * taskId 必填；formData 由父页面从 iframe 收集；message 对应 FlowParams.message。
 */
public class GodSubmitRequest {
    private String taskId;

    private String message;

    private Map<String, Object> formData = new LinkedHashMap<>();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getFormData() {
        return formData;
    }

    public void setFormData(Map<String, Object> formData) {
        this.formData = formData;
    }
}
