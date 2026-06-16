package org.dromara.warm.flow.core.condition;

import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AbstractConditionStrategyTest {

    @Test
    public void shouldEvaluateNestedFormDataCondition() {
        Map<String, Object> formData = new HashMap<>();
        formData.put("leaveDays", "4");

        Map<String, Object> variable = new HashMap<>();
        variable.put("formData", formData);

        Assert.assertTrue(ExpressionUtil.evalCondition("eq@@formData.leaveDays|4", variable));
    }
}
