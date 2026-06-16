package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;
import org.springframework.stereotype.Component;

/**
 * 直接读取引擎里的已发布表单。
 */
@Component
public class WarmFlowPublishedFormReader implements DemoPublishedFormReader {

    @Override
    public Page<Form> readPublishedForms() {
        return FlowEngine.formService().publishedPage(null, 1, 200);
    }
}
