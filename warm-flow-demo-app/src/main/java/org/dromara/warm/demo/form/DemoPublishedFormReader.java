package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;

/**
 * 已发布表单读取抽象。
 */
public interface DemoPublishedFormReader {

    Page<Form> readPublishedForms();
}
