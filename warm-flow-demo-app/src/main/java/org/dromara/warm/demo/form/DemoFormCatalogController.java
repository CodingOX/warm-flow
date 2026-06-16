package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.dto.ApiResult;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * demo 侧的表单目录查询接口。
 */
@RestController
@RequestMapping("/demo/forms")
public class DemoFormCatalogController {

    private final DemoPublishedFormReader reader;

    public DemoFormCatalogController(DemoPublishedFormReader reader) {
        this.reader = reader;
    }

    @GetMapping("/published")
    public ApiResult<Page<Form>> published() {
        return ApiResult.ok(reader.readPublishedForms());
    }
}
