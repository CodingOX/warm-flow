package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.orm.entity.FlowForm;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormPathQueryServiceImplTest {

    @Test
    void queryFormPath_shouldUseFormIdAsTreeId() {
        DemoPublishedFormReader reader = () -> {
            Form form = new FlowForm()
                .setId(12L)
                .setFormName("请假发起表单")
                .setIsPublish(1);
            return new Page<>(Collections.singletonList(form), 1);
        };

        FormPathQueryServiceImpl service = new FormPathQueryServiceImpl(reader);

        assertEquals(1, service.queryFormPath().size());
        assertEquals("12", service.queryFormPath().get(0).getId());
        assertEquals("请假发起表单", service.queryFormPath().get(0).getName());
        assertEquals("0", service.queryFormPath().get(0).getParentId());
    }
}
