/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.flow.ui.service;

import org.dromara.warm.flow.core.dto.Tree;

import java.util.List;

/**
 * 动态表单列表接口
 *
 * 约定：
 * 1. 返回给设计器的树结构用于 `formCustom = 'Y'` 的动态表单下拉；
 * 2. Tree.value 必须是已发布动态表单主键 ID；
 * 3. Tree.label / name 用于前端展示；
 * 4. 前端选中后会把该值写入 definition / node 的 formPath 字段。
 *
 * @author warm
 * @since 2025/10/22
 */
public interface FormPathService {

    /**
     * 查询设计器可选的动态表单树
     *
     * @return 动态表单树，value 为动态表单主键 ID
     */
    List<Tree> queryFormPath();
}
