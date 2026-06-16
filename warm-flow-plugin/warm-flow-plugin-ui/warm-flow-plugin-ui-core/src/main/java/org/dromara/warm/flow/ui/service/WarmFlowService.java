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

import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.dto.*;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.FormCustomEnum;
import org.dromara.warm.flow.core.enums.ModelEnum;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.utils.ExceptionUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.ui.dto.HandlerFeedBackDto;
import org.dromara.warm.flow.ui.dto.HandlerQuery;
import org.dromara.warm.flow.ui.utils.TreeUtil;
import org.dromara.warm.flow.ui.vo.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 设计器Controller 可选择是否放行，放行可与业务系统共享权限，主要是用来访问业务系统数据
 *
 * @author warm
 */
@Slf4j
    public class WarmFlowService {

    private static final Pattern FORM_DATA_REF_PATTERN = Pattern.compile("\\b" + FlowCons.FORM_DATA + "\\.([a-zA-Z_][\\w]*)\\b");
    private static final Pattern SIMPLE_BARE_FIELD_PATTERN = Pattern.compile("^(gt|ge|eq|ne|lt|le|like|notLike)@@([a-zA-Z_][\\w]*)\\|.*$");
    private static final int FORM_CODE_MAX_LENGTH = 40;

    /**
     * 返回流程定义的配置
     *
     * @return ApiResult<WarmFlowVo>
     */
    public static ApiResult<WarmFlowVo> config() {
        WarmFlowVo warmFlowVo = new WarmFlowVo();
        WarmFlow warmFlow = FlowEngine.getFlowConfig();
        warmFlowVo.setFramework(warmFlow.getFramework().name());
        // 获取tokenName
        String tokenName = warmFlow.getTokenName();
        if (StringUtils.isEmpty(tokenName)) {
            return ApiResult.fail("未配置tokenName");
        }
        String[] tokenNames = tokenName.split(",");
        List<String> tokenNameList = Arrays.stream(tokenNames).filter(StringUtils::isNotEmpty)
            .map(String::trim).collect(Collectors.toList());
        warmFlowVo.setTokenNameList(tokenNameList);

        return ApiResult.ok(warmFlowVo);
    }

    /**
     * 保存流程json字符串
     *
     * @param defJson      流程数据集合
     * @param onlyNodeSkip 是否只保存节点和跳转
     * @return ApiResult<Void>
     * @throws Exception 异常
     * @author xiarg
     * @since 2024/10/29 16:31
     */
    public static ApiResult<Void> saveJson(DefJson defJson, boolean onlyNodeSkip) throws Exception {
        validateDynamicFormConditions(defJson);
        FlowEngine.defService().saveDef(defJson, onlyNodeSkip);
        return ApiResult.ok();
    }

    private static void validateDynamicFormConditions(DefJson defJson) {
        if (!hasDynamicFormBinding(defJson)) {
            return;
        }
        Map<String, Set<String>> formFieldsCache = new HashMap<>();
        if (defJson.getNodeList() == null) {
            return;
        }
        for (NodeJson nodeJson : defJson.getNodeList()) {
            Set<String> nodeFields = loadEffectiveFormFields(defJson, nodeJson, formFieldsCache);
            if (StringUtils.isEmpty(resolveEffectiveFormId(defJson, nodeJson))) {
                continue;
            }
            scanSkipConditions(nodeJson, nodeFields);
        }
    }

    static boolean hasDynamicFormBinding(DefJson defJson) {
        if (defJson == null) {
            return false;
        }
        if (FlowCons.FORM_CUSTOM_Y.equals(defJson.getFormCustom()) && StringUtils.isNotEmpty(defJson.getFormPath())) {
            return true;
        }
        if (defJson.getNodeList() == null) {
            return false;
        }
        for (NodeJson nodeJson : defJson.getNodeList()) {
            if (nodeJson != null && FlowCons.FORM_CUSTOM_Y.equals(nodeJson.getFormCustom())
                && StringUtils.isNotEmpty(nodeJson.getFormPath())) {
                return true;
            }
        }
        return false;
    }

    private static void scanSkipConditions(NodeJson nodeJson, Set<String> formFields) {
        if (nodeJson == null || nodeJson.getSkipList() == null || nodeJson.getSkipList().isEmpty()) {
            return;
        }
        for (SkipJson skipJson : nodeJson.getSkipList()) {
            if (skipJson == null || StringUtils.isEmpty(skipJson.getSkipCondition())) {
                continue;
            }
            validateConditionText(nodeJson, skipJson, formFields);
        }
    }

    private static void validateConditionText(NodeJson nodeJson, SkipJson skipJson, Set<String> formFields) {
        String condition = skipJson.getSkipCondition();
        if (StringUtils.isEmpty(condition)) {
            return;
        }
        if (condition.contains("form.")) {
            throw new FlowException(buildConditionError(nodeJson, skipJson, "动态表单字段引用必须使用 formData.<field> 形式"));
        }
        Set<String> refs = extractFormDataRefs(condition);
        for (String ref : refs) {
            if (!formFields.contains(ref)) {
                throw new FlowException(buildConditionError(nodeJson, skipJson, "当前动态表单未包含字段：" + ref));
            }
        }
        String bareRef = extractSimpleBareRef(condition);
        if (StringUtils.isNotEmpty(bareRef) && formFields.contains(bareRef)) {
            throw new FlowException(buildConditionError(nodeJson, skipJson, "动态表单字段引用必须使用 formData.<field> 形式"));
        }
    }

    private static String buildConditionError(NodeJson nodeJson, SkipJson skipJson, String reason) {
        String nodeName = nodeJson == null ? "" : StringUtils.emptyDefault(nodeJson.getNodeName(), nodeJson.getNodeCode());
        String skipName = skipJson == null ? "" : StringUtils.emptyDefault(skipJson.getSkipName(), skipJson.getNextNodeCode());
        return "节点【" + nodeName + "】跳转【" + skipName + "】" + reason;
    }

    static Set<String> extractFormDataRefs(String condition) {
        Set<String> refs = new HashSet<>();
        if (StringUtils.isEmpty(condition)) {
            return refs;
        }
        Matcher matcher = FORM_DATA_REF_PATTERN.matcher(condition);
        while (matcher.find()) {
            refs.add(matcher.group(1));
        }
        return refs;
    }

    static String extractSimpleBareRef(String condition) {
        if (StringUtils.isEmpty(condition) || condition.contains(FlowCons.FORM_DATA + ".")) {
            return null;
        }
        Matcher matcher = SIMPLE_BARE_FIELD_PATTERN.matcher(condition);
        return matcher.matches() ? matcher.group(2) : null;
    }

    private static Set<String> loadEffectiveFormFields(DefJson defJson, NodeJson nodeJson, Map<String, Set<String>> formFieldsCache) {
        String formId = resolveEffectiveFormId(defJson, nodeJson);
        if (StringUtils.isEmpty(formId)) {
            return Collections.emptySet();
        }
        try {
            Long id = Long.valueOf(formId);
            if (formFieldsCache.containsKey(formId)) {
                return formFieldsCache.get(formId);
            }
            Form form = FlowEngine.formService().getById(id);
            if (form == null || StringUtils.isEmpty(form.getFormContent())) {
                formFieldsCache.put(formId, Collections.emptySet());
                return Collections.emptySet();
            }
            Set<String> fields = extractFormFields(form.getFormContent());
            formFieldsCache.put(formId, fields);
            return fields;
        } catch (Exception e) {
            throw new FlowException("读取动态表单字段失败，请检查表单是否存在且内容合法", e);
        }
    }

    private static String resolveEffectiveFormId(DefJson defJson, NodeJson nodeJson) {
        if (nodeJson != null && FlowCons.FORM_CUSTOM_Y.equals(nodeJson.getFormCustom()) && StringUtils.isNotEmpty(nodeJson.getFormPath())) {
            return nodeJson.getFormPath();
        }
        if (defJson != null && FlowCons.FORM_CUSTOM_Y.equals(defJson.getFormCustom()) && StringUtils.isNotEmpty(defJson.getFormPath())) {
            return defJson.getFormPath();
        }
        return null;
    }

    static Set<String> extractFormFields(String formContent) {
        Set<String> fields = new HashSet<>();
        if (StringUtils.isEmpty(formContent)) {
            return fields;
        }
        Map<String, Object> content = FlowEngine.jsonConvert.strToMap(formContent);
        Object ruleObj = content.get("rule");
        if (!(ruleObj instanceof List<?>)) {
            return fields;
        }
        List<?> ruleList = (List<?>) ruleObj;
        for (Object item : ruleList) {
            collectFormFields(item, fields);
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static void collectFormFields(Object item, Set<String> fields) {
        if (!(item instanceof Map<?, ?>)) {
            return;
        }
        Map<?, ?> ruleMap = (Map<?, ?>) item;
        Object fieldObj = ruleMap.get("field");
        if (fieldObj != null && StringUtils.isNotEmpty(fieldObj.toString())) {
            fields.add(fieldObj.toString().trim());
        }
        Object childrenObj = ruleMap.get("children");
        if (childrenObj instanceof List<?>) {
            for (Object child : (List<Object>) childrenObj) {
                collectFormFields(child, fields);
            }
        }
        Object propsObj = ruleMap.get("props");
        if (propsObj instanceof Map<?, ?>) {
            Map<?, ?> propsMap = (Map<?, ?>) propsObj;
            Object optionsChildren = propsMap.get("children");
            if (optionsChildren instanceof List<?>) {
                for (Object child : (List<Object>) optionsChildren) {
                    collectFormFields(child, fields);
                }
            }
        }
    }

    /**
     * 获取流程定义数据(包含节点和跳转)
     *
     * @param id 流程定义id
     * @return ApiResult<DefVo>
     * @author xiarg
     * @since 2024/10/29 16:31
     */
    public static ApiResult<DefJson> queryDef(Long id) {
        try {
            DefJson defJson;
            if (id == null) {
                defJson = new DefJson()
                    .setModelValue(ModelEnum.CLASSICS.name())
                    .setFormCustom(FormCustomEnum.N.name());
            } else {
                defJson = FlowEngine.defService().queryDesign(id);
            }
            CategoryService categoryService = FrameInvoker.getBean(CategoryService.class);
            if (categoryService != null) {
                List<Tree> treeList = categoryService.queryCategory();
                defJson.setCategoryList(TreeUtil.buildTree(treeList));
            }
            // 设计器里的动态表单下拉统一走 formPathList。
            // 约定 Tree.value 为动态表单主键 ID，前端在 formCustom = 'Y' 时写入 formPath。
            FormPathService formPathService = FrameInvoker.getBean(FormPathService.class);
            if (formPathService != null) {
                List<Tree> treeList = formPathService.queryFormPath();
                defJson.setFormPathList(TreeUtil.buildTree(treeList));
                if (id == null) {
                    defJson.setFormCustom(FormCustomEnum.Y.name());
                }
            }
            return ApiResult.ok(defJson);
        } catch (Exception e) {
            log.error("获取流程json字符串", e);
            throw new FlowException(ExceptionUtil.handleMsg("获取流程json字符串失败", e));
        }
    }

    /**
     * 获取流程图
     *
     * @param id 流程实例id
     * @return ApiResult<DefJson>
     */
    public static ApiResult<DefJson> queryFlowChart(Long id) {
        try {
            Instance instance = FlowEngine.insService().getById(id);
            String defJsonStr = instance.getDefJson();
            DefJson defJson = FlowEngine.jsonConvert.strToBean(defJsonStr, DefJson.class);
            defJson.setInstance(instance);

            // 获取流程图三原色
            defJson.setChartStatusColor(FlowEngine.chartService().getChartRgb(defJson.getModelValue()));
            // 是否显示流程图顶部文字
            defJson.setTopTextShow(FlowEngine.getFlowConfig().isTopTextShow());
            // 需要业务系统实现该接口
            ChartExtService chartExtService = FrameInvoker.getBean(ChartExtService.class);
            if (chartExtService != null) {
                chartExtService.initPromptContent(defJson);
                chartExtService.execute(defJson);
            }

            return ApiResult.ok(defJson);
        } catch (Exception e) {
            log.error("获取流程图", e);
            throw new FlowException(ExceptionUtil.handleMsg("获取流程图失败", e));
        }
    }

    /**
     * 办理人权限设置列表tabs页签
     *
     * @return List<String>
     */
    public static ApiResult<List<String>> handlerType() {
        try {
            // 需要业务系统实现该接口
            HandlerSelectService handlerSelectService = FrameInvoker.getBean(HandlerSelectService.class);
            if (handlerSelectService == null) {
                return ApiResult.ok(Collections.emptyList());
            }
            List<String> handlerType = handlerSelectService.getHandlerType();
            return ApiResult.ok(handlerType);
        } catch (Exception e) {
            log.error("办理人权限设置列表tabs页签异常", e);
            throw new FlowException(ExceptionUtil.handleMsg("办理人权限设置列表tabs页签失败", e));
        }
    }

    /**
     * 办理人权限设置列表结果
     *
     * @return HandlerSelectVo
     */
    public static ApiResult<HandlerSelectVo> handlerResult(HandlerQuery query) {
        try {
            // 需要业务系统实现该接口
            HandlerSelectService handlerSelectService = FrameInvoker.getBean(HandlerSelectService.class);
            if (handlerSelectService == null) {
                return ApiResult.ok(new HandlerSelectVo());
            }
            HandlerSelectVo handlerSelectVo = handlerSelectService.getHandlerSelect(query);
            return ApiResult.ok(handlerSelectVo);
        } catch (Exception e) {
            log.error("办理人权限设置列表结果异常", e);
            throw new FlowException(ExceptionUtil.handleMsg("办理人权限设置列表结果失败", e));
        }
    }

    /**
     * 办理人权限名称回显
     *
     * @return HandlerSelectVo
     */
    public static ApiResult<List<HandlerFeedBackVo>> handlerFeedback(HandlerFeedBackDto handlerFeedBackDto) {
        try {
            // 需要业务系统实现该接口
            HandlerSelectService handlerSelectService = FrameInvoker.getBean(HandlerSelectService.class);
            if (handlerSelectService == null) {
                List<HandlerFeedBackVo> handlerFeedBackVos = StreamUtils.toList(handlerFeedBackDto.getStorageIds(),
                    storageId -> new HandlerFeedBackVo(storageId, null));
                return ApiResult.ok(handlerFeedBackVos);
            }
            List<HandlerFeedBackVo> handlerFeedBackVos = handlerSelectService.handlerFeedback(handlerFeedBackDto.getStorageIds());
            return ApiResult.ok(handlerFeedBackVos);
        } catch (Exception e) {
            log.error("办理人权限名称回显", e);
            throw new FlowException(ExceptionUtil.handleMsg("办理人权限名称回显", e));
        }
    }

    /**
     * 办理人选择项
     *
     * @return List<Dict>
     */
    public static ApiResult<List<Dict>> handlerDict() {
        try {
            // 需要业务系统实现该接口
            HandlerDictService handlerDictService = FrameInvoker.getBean(HandlerDictService.class);
            if (handlerDictService == null) {
                List<Dict> dictList = new ArrayList<>();
                Dict dict = new Dict();
                dict.setLabel("默认表达式");
                dict.setValue("${handler}");
                Dict dict1 = new Dict();
                dict1.setLabel("spel表达式");
                dict1.setValue("#{@user.evalVar(#handler)}");
                Dict dict2 = new Dict();
                dict2.setLabel("其他");
                dict2.setValue("");
                dictList.add(dict);
                dictList.add(dict1);
                dictList.add(dict2);

                return ApiResult.ok(dictList);
            }
            return ApiResult.ok(handlerDictService.getHandlerDict());
        } catch (Exception e) {
            log.error("办理人权限设置列表结果异常", e);
            throw new FlowException(ExceptionUtil.handleMsg("办理人权限设置列表结果失败", e));
        }
    }

    /**
     * 已发布表单列表 该接口不需要业务系统实现
     */
    public static ApiResult<List<Form>> publishedForm() {
        try {
            return ApiResult.ok(FlowEngine.formService().list(FlowEngine.newForm().setIsPublish(1)));
        } catch (Exception e) {
            log.error("已发布表单列表异常", e);
            throw new FlowException(ExceptionUtil.handleMsg("已发布表单列表异常", e));
        }
    }

    /**
     * 读取表单内容
     *
     * @param id
     * @return
     */
    public static ApiResult<String> getFormContent(Long id) {
        try {
            Form form = FlowEngine.formService().getById(id);
            return ApiResult.ok(form == null ? null : form.getFormContent());
        } catch (Exception e) {
            log.error("获取表单内容字符串", e);
            throw new FlowException(ExceptionUtil.handleMsg("获取表单内容字符串失败", e));
        }
    }

    /**
     * 保存表单内容,该接口不需要系统实现
     *
     * @param flowDto
     * @return
     */
    public static ApiResult<FlowDto> saveFormContent(FlowDto flowDto) {
        try {
            String formContent = flowDto == null ? null : flowDto.getFormContent();
            Form form = null;
            if (flowDto != null && flowDto.getId() != null) {
                form = FlowEngine.formService().getById(flowDto.getId());
            }

            Long formId;
            if (form == null) {
                form = createDraftForm(formContent);
                formId = form.getId();
            } else {
                syncDraftFormName(form, formContent);
                FlowEngine.formService().saveContent(form.getId(), formContent);
                formId = form.getId();
            }

            FlowDto result = new FlowDto();
            result.setId(formId);
            return ApiResult.ok(result);
        } catch (Exception e) {
            log.error("保存表单内容", e);
            throw new FlowException(ExceptionUtil.handleMsg("保存表单内容失败", e));
        }
    }

    /**
     * type=form 页面允许直接进入空白创建态，因此首次保存时需要自动补一条草稿表单记录。
     */
    private static Form createDraftForm(String formContent) {
        Form form = FlowEngine.newForm();
        if (FlowEngine.dataFillHandler() != null) {
            FlowEngine.dataFillHandler().idFill(form);
        }
        Long draftId = ensureDraftId(form);
        form.setFormCode(resolveDraftFormCode(formContent, draftId));
        form.setFormName(resolveDraftFormName(formContent, draftId));
        form.setFormType(0);
        form.setIsPublish(0);
        form.setFormContent(formContent);
        FlowEngine.formService().save(form);
        return form;
    }

    private static Long ensureDraftId(Form form) {
        if (form.getId() != null) {
            return form.getId();
        }
        Long draftId = System.currentTimeMillis();
        form.setId(draftId);
        return draftId;
    }

    static String buildDraftFormCode(Long draftId) {
        return "FORM_" + draftId;
    }

    static String resolveDraftFormCode(String formContent, Long draftId) {
        String formName = extractFormName(formContent);
        String normalizedCode = normalizeFormCode(formName);
        return StringUtils.isNotEmpty(normalizedCode) ? normalizedCode : buildDraftFormCode(draftId);
    }

    static String normalizeFormCode(String formName) {
        if (StringUtils.isEmpty(formName)) {
            return null;
        }
        String code = formName.trim().replaceAll("[^a-zA-Z0-9_]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        if (Character.isDigit(code.charAt(0))) {
            code = "FORM_" + code;
        }
        return code.length() > FORM_CODE_MAX_LENGTH ? code.substring(0, FORM_CODE_MAX_LENGTH) : code;
    }

    static String buildDraftFormName(Long draftId) {
        return "未命名表单_" + draftId;
    }

    static String extractFormName(String formContent) {
        if (StringUtils.isEmpty(formContent) || FlowEngine.jsonConvert == null) {
            return null;
        }
        try {
            Map<String, Object> content = FlowEngine.jsonConvert.strToMap(formContent);
            if (content == null) {
                return null;
            }
            Object optionObj = content.get("option");
            if (!(optionObj instanceof Map<?, ?>)) {
                return null;
            }
            Object formNameObj = ((Map<?, ?>) optionObj).get("formName");
            if (formNameObj == null) {
                return null;
            }
            String formName = formNameObj.toString().trim();
            return StringUtils.isEmpty(formName) ? null : formName;
        } catch (Exception e) {
            log.warn("解析表单名称失败，将回退到默认命名", e);
            return null;
        }
    }

    static String resolveDraftFormName(String formContent, Long draftId) {
        String formName = extractFormName(formContent);
        return StringUtils.isNotEmpty(formName) ? formName : buildDraftFormName(draftId);
    }

    private static void syncDraftFormName(Form form, String formContent) {
        if (form == null || StringUtils.isEmpty(formContent) || form.getIsPublish() == null || form.getIsPublish() != 0) {
            return;
        }
        String parsedFormName = extractFormName(formContent);
        if (StringUtils.isEmpty(parsedFormName) || parsedFormName.equals(form.getFormName())) {
            return;
        }
        form.setFormName(parsedFormName);
        FlowEngine.formService().updateById(form);
    }


    /**
     * 根据任务id获取待办任务表单及数据
     *
     * @param taskId 当前任务id
     * @return {@link ApiResult<FlowDto>}
     * @author liangli
     * @date 2024/8/21 17:08
     **/
    public static ApiResult<FlowDto> load(Long taskId) {
        FlowParams flowParams = FlowParams.build();

        return ApiResult.ok(FlowEngine.taskService().load(taskId, flowParams));
    }

    /**
     * 根据任务id获取已办任务表单及数据
     *
     * @param hisTaskId
     * @return
     */
    public static ApiResult<FlowDto> hisLoad(Long hisTaskId) {
        FlowParams flowParams = FlowParams.build();

        return ApiResult.ok(FlowEngine.taskService().hisLoad(hisTaskId, flowParams));
    }

    /**
     * 通用表单流程审批接口
     *
     * @param formData
     * @param taskId
     * @param skipType
     * @param message
     * @param nodeCode
     * @return
     */
    public static ApiResult<Instance> handle(Map<String, Object> formData, Long taskId, String skipType
        , String message, String nodeCode) {
        FlowParams flowParams = FlowParams.build()
            .skipType(skipType)
            .nodeCode(nodeCode)
            .message(message);

        flowParams.formData(formData);

        return ApiResult.ok(FlowEngine.taskService().skip(taskId, flowParams));
    }

    /**
     * 获取节点扩展属性
     *
     * @return List<NodeExt>
     */
    public static ApiResult<List<NodeExt>> nodeExt() {
        try {
            // 需要业务系统实现该接口
            NodeExtService nodeExtService = FrameInvoker.getBean(NodeExtService.class);
            if (nodeExtService == null) {
                return ApiResult.ok(Collections.emptyList());
            }
            List<NodeExt> nodeExts = nodeExtService.getNodeExt();
            return ApiResult.ok(nodeExts);
        } catch (Exception e) {
            log.error("获取节点扩展属性", e);
            throw new FlowException(ExceptionUtil.handleMsg("获取节点扩展属性失败", e));
        }
    }

    /**
     * 获取监听器列表
     *
     * @return List<NodeExt>
     */
    public static ApiResult<List<ListenerVo>> listenerList() {
        try {
            // 需要业务系统实现该接口
            ListenerListService listenerListService = FrameInvoker.getBean(ListenerListService.class);
            if (listenerListService == null) {
                return ApiResult.ok(Collections.emptyList());
            }
            List<ListenerVo> listenerList = listenerListService.listenerList();
            return ApiResult.ok(listenerList);
        } catch (Exception e) {
            log.error("获取监听器列表", e);
            throw new FlowException(ExceptionUtil.handleMsg("获取监听器列表失败", e));
        }
    }

}
