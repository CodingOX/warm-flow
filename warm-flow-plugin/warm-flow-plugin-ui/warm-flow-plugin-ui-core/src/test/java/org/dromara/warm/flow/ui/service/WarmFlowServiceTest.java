package org.dromara.warm.flow.ui.service;

import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.json.JsonConvert;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WarmFlowServiceTest {

    @Test
    public void shouldDetectNodeLevelDynamicFormBinding() {
        DefJson defJson = new DefJson()
            .setFormCustom("N");
        defJson.getNodeList().add(new NodeJson()
            .setFormCustom("Y")
            .setFormPath("1001"));

        Assert.assertTrue(WarmFlowService.hasDynamicFormBinding(defJson));
    }

    @Test
    public void shouldExtractFormDataRefsFromExpression() {
        Set<String> refs = WarmFlowService.extractFormDataRefs("default@@${formData.leaveDays > 3 && formData.level == 2}");

        Assert.assertEquals(2, refs.size());
        Assert.assertTrue(refs.contains("leaveDays"));
        Assert.assertTrue(refs.contains("level"));
    }

    @Test
    public void shouldExtractBareRefOnlyForSimpleCondition() {
        Assert.assertEquals("leaveDays", WarmFlowService.extractSimpleBareRef("gt@@leaveDays|3"));
        Assert.assertNull(WarmFlowService.extractSimpleBareRef("default@@${leaveDays > 3}"));
        Assert.assertNull(WarmFlowService.extractSimpleBareRef("default@@${formData.leaveDays > 3}"));
    }

    @Test
    public void shouldBuildDraftFormCodeFromDraftId() {
        Assert.assertEquals("FORM_123", WarmFlowService.buildDraftFormCode(123L));
    }

    @Test
    public void shouldBuildDraftFormNameFromDraftId() {
        Assert.assertEquals("未命名表单_123", WarmFlowService.buildDraftFormName(123L));
    }

    @Test
    public void shouldResolveDraftFormNameFromFormContentOption() {
        FlowEngine.jsonConvert = new JsonConvert() {
            @Override
            public Map<String, Object> strToMap(String jsonStr) {
                Map<String, Object> option = new HashMap<>();
                option.put("formName", "leave_form_1");
                Map<String, Object> content = new HashMap<>();
                content.put("option", option);
                return content;
            }

            @Override
            public <T> T strToBean(String jsonStr, Class<T> clazz) {
                return null;
            }

            @Override
            public <T> List<T> strToList(String jsonStr) {
                return null;
            }

            @Override
            public String objToStr(Object variable) {
                return null;
            }
        };

        Assert.assertEquals("leave_form_1", WarmFlowService.resolveDraftFormName("{\"option\":{\"formName\":\"leave_form_1\"}}", 123L));
    }
}
