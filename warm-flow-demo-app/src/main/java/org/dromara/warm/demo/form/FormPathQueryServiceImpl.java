package org.dromara.warm.demo.form;

import org.dromara.warm.flow.core.dto.Tree;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.utils.page.Page;
import org.dromara.warm.flow.ui.service.FormPathService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 把已发布表单映射成设计器可消费的树节点。
 */
@Service
public class FormPathQueryServiceImpl implements FormPathService {

    private final DemoPublishedFormReader reader;

    public FormPathQueryServiceImpl(DemoPublishedFormReader reader) {
        this.reader = reader;
    }

    @Override
    public List<Tree> queryFormPath() {
        Page<Form> page = reader.readPublishedForms();
        if (page == null || page.getList() == null) {
            return Collections.emptyList();
        }
        return page.getList().stream()
            .filter(Objects::nonNull)
            .map(this::toTree)
            .collect(Collectors.toList());
    }

    private Tree toTree(Form form) {
        return new Tree()
            .setId(String.valueOf(form.getId()))
            .setName(form.getFormName())
            .setParentId("0");
    }
}
