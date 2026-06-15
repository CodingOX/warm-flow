package org.dromara.warm.flow.ui.service;

import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.junit.Assert;
import org.junit.Test;

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
}
