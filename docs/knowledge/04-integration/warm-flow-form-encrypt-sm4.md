# Warm-Flow 自定义表单敏感数据 SM4 加密方案

## 1. 问题背景

Warm-Flow 通过 form-create 自定义表单收集用户填报数据（如手机号、身份证号、银行卡号、密码等），这些数据以**明文 JSON** 形式存储在数据库中，存在安全隐患。

### 1.1 受影响的数据

| 表 | 列 | 存储内容 |
|---|-----|---------|
| `flow_instance` | `variable` (text) | 流程实例变量，包含 `formData` 键 |
| `flow_his_task` | `variable` (text) | 历史任务审批时的变量快照，包含 `formData` 键 |

### 1.2 典型敏感字段

| 字段类型 | 字段名常见命名 | 值正则模式 |
|---------|-------------|----------|
| 手机号 | `phone`, `mobile`, `tel`, `telephone` | `^1[3-9]\d{9}$` |
| 身份证号 (18位) | `idCard`, `idNumber`, `identityCard` | `^\d{17}[\dXx]$` |
| 身份证号 (15位) | 同上 | `^\d{15}$` |
| 银行卡号 | `bankCard`, `bankAccount` | `^\d{16,19}$` |
| 电子邮箱 | `email` | `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$` |
| 密码 | `password`, `pwd`, `passwd` | 任意字符串 |

## 2. 数据流全景

### 2.1 写入链路（用户提交表单）

```
┌──────────────────────────────────────────────────────────┐
│  前端 formCreate                                          │
│  POST /warm-flow/execute/handle                           │
│  Body: {"name":"张三", "phone":"13800138000",             │
│         "idCard":"110101199001011234", "reason":"年假"}   │
└────────────────────────┬─────────────────────────────────┘
                         │ Map<String, Object> formData
                         ▼
┌──────────────────────────────────────────────────────────┐
│  WarmFlowController.handle()                              │
│  接收 @RequestBody Map<String, Object> formData          │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│  WarmFlowService.handle()                                 │
│  → flowParams.formData(formData)                          │
│  → variable["formData"] = formData                        │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│  TaskServiceImpl.skip()                                   │
│  → 执行 GlobalListener.start(ListenerVariable)            │ ← variable 含 formData
│  → 执行 GlobalListener.assignment(ListenerVariable)       │
│  → mergeVariable(instance, flowParams.getVariable())     │ ← 【持久化点】
│  → 执行 GlobalListener.finish(ListenerVariable)           │
│  → 执行 GlobalListener.create(ListenerVariable)           │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│  flow_instance.variable =                                 │
│  '{"formData":{"name":"张三","phone":"13800138000",...}}'│
│  ← 明文 JSON 写入 TEXT 列                                │
└──────────────────────────────────────────────────────────┘
```

### 2.2 读取链路（加载表单数据）

```
┌──────────────────────────────────────────────────────────┐
│  GET /warm-flow/execute/load/{taskId}                    │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│  WarmFlowService.load(taskId)                             │
│  → TaskServiceImpl.load(taskId, flowParams)              │
│    → 执行 LISTENER_FORM_LOAD 监听器                       │
│    → flow_form.form_content → form (表单模板)            │
│    → instance.variable["formData"] → data (填报数据)     │
│  ← FlowDto { form: Form, data: Map }                     │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│  前端收到明文 formData 进行回显                            │
│  {"name":"张三","phone":"13800138000","idCard":"...",...} │
└──────────────────────────────────────────────────────────┘
```

## 3. 加密方案设计

### 3.1 核心思路

```
  写入时: GlobalListener.finish() 拦截
  ─────────────────────────────────────
  遍历 formData 所有 key-value
    ├── 字段名命中敏感词库  → SM4 加密, 值替换为 "ENC:SM4:{ciphertext}"
    ├── 值匹配敏感正则    → SM4 加密, 值替换为 "ENC:SM4:{ciphertext}"
    └── 不匹配            → 保持原值


  读取时: WarmFlowService.load()/hisLoad() 拦截
  ─────────────────────────────────────────────────
  遍历返回的 data (即 formData) 所有 key-value
    ├── 值前缀为 "ENC:SM4:"  → SM4 解密, 值替换为原文
    └── 无前缀              → 保持原值
```

### 3.2 为什么不全部加密

- 条件表达式 `${formData.reason == '年假'}` 需要读取非敏感字段
- 审批记录中需要展示非敏感信息（如请假天数、申请原因）
- 只加密敏感字段，对系统其他功能**零影响**

### 3.3 加密算法选型：SM4

| 维度 | SM4 | AES-256-GCM |
|------|-----|------------|
| **性质** | 国密标准（GM/T 0002-2012） | 国际标准 |
| **安全级别** | 等同于 AES-128 | AES-256 更高 |
| **合规** | 国内金融/政务强制要求 | 国内无强制 |
| **依赖** | `hutool-crypto` + `bcprov-jdk18on` | JDK 内置 |
| **已有基建** | RuoYi-Vue-Plus 已封装 `EncryptUtils.encryptBySm4()` | RuoYi-Vue-Plus 也已有 |

**选择 SM4 的理由**：国内合规要求 + RuoYi-Vue-Plus 已有成熟封装，直接复用。

SM4 核心参数：
- **模式**: ECB（Electronic Codebook）
- **密钥**: 128 bit（16 字节），从 `application.yml` 配置读取
- **编码**: Base64（加密后输出）
- **依赖**: `cn.hutool:hutool-crypto` 内部调用 `org.bouncycastle:bcprov-jdk18on`

## 4. 敏感数据识别策略

采用**字段名优先 + 值正则兜底**的混合策略，兼顾准确性和覆盖面。

### 4.1 字段名敏感词库（高置信度）

```java
private static final Set<String> SENSITIVE_KEY_NAMES = Set.of(
    // 手机号
    "phone", "mobile", "tel", "telephone", "phoneNumber", "cellphone",
    // 身份证
    "idCard", "idNumber", "identityCard", "idNo", "identity",
    // 银行卡
    "bankCard", "bankAccount", "bankNo", "cardNumber",
    // 密码
    "password", "pwd", "passwd", "secret",
    // 邮箱
    "email", "mail"
);
```

### 4.2 值正则匹配库（兜底）

```java
private static final Pattern[] SENSITIVE_VALUE_PATTERNS = {
    Pattern.compile("^1[3-9]\\d{9}$"),                        // 中国大陆手机号
    Pattern.compile("^\\d{17}[\\dXx]$"),                      // 18 位身份证
    Pattern.compile("^\\d{15}$"),                              // 15 位身份证（旧）
    Pattern.compile("^\\d{16,19}$"),                           // 银行卡号（16-19 位）
    Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")  // 邮箱
};
```

### 4.3 识别流程

```
isSensitive(key, value):
    1. key 在 SENSITIVE_KEY_NAMES 中 → true
    2. value 匹配任一 SENSITIVE_VALUE_PATTERNS → true
    3. 否则 → false
```

### 4.4 防重复加密

以 `"ENC:SM4:"` 为前缀标记已加密值，识别时跳过：

```java
if (value.startsWith("ENC:SM4:")) {
    return false;  // 已加密，不重复处理
}
```

## 5. 实现方案

### 5.1 核心工具类

文件位置：建议放在 `warm-flow-plugin-ui/warm-flow-plugin-ui-core/src/main/java/org/dromara/warm/flow/ui/crypto/` 下。

```java
package org.dromara.warm.flow.ui.crypto;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import org.dromara.warm.flow.core.constant.FlowCons;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 流程表单敏感数据 SM4 加密/解密工具
 * <p>
 * 在 GlobalListener.finish() 中调用 encrypt() 持久化前加密，
 * 在 WarmFlowService.load()/hisLoad() 中调用 decrypt() 返回前端前解密。
 * </p>
 *
 * @author team
 */
public final class FormDataSm4Encryptor {

    private static final String ENC_PREFIX = "ENC:SM4:";

    // ─── SM4 实例（密钥从配置读取） ───
    private static volatile SymmetricCrypto sm4;

    // ─── 字段名敏感词库 ───
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "phone", "mobile", "tel", "telephone", "phoneNumber", "cellphone",
        "idCard", "idNumber", "identityCard", "idNo", "identity",
        "bankCard", "bankAccount", "bankNo", "cardNumber",
        "password", "pwd", "passwd", "secret",
        "email", "mail"
    );

    // ─── 值正则匹配库 ───
    private static final Pattern[] SENSITIVE_PATTERNS = {
        Pattern.compile("^1[3-9]\\d{9}$"),
        Pattern.compile("^\\d{17}[\\dXx]$"),
        Pattern.compile("^\\d{15}$"),
        Pattern.compile("^\\d{16,19}$"),
        Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    };

    /**
     * 初始化 SM4 密钥（在项目启动时调用，如 @PostConstruct 中）
     *
     * @param key 16 字节 SM4 密钥
     */
    public static void init(String key) {
        sm4 = SmUtil.sm4(key.getBytes());
    }

    /**
     * 判断字段值是否为敏感数据
     */
    public static boolean isSensitive(String key, String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.startsWith(ENC_PREFIX)) return false; // 已加密

        // 优先：字段名命中
        if (SENSITIVE_KEYS.contains(key)) return true;

        // 兜底：值匹配正则
        for (Pattern p : SENSITIVE_PATTERNS) {
            if (p.matcher(value).matches()) return true;
        }
        return false;
    }

    /**
     * 加密 flow_instance.variable 中的 formData 敏感字段
     * 在 GlobalListener.finish() 中调用
     */
    @SuppressWarnings("unchecked")
    public static void encryptVariable(Map<String, Object> variable) {
        if (variable == null || sm4 == null) return;
        Object raw = variable.get(FlowCons.FORM_DATA);
        if (!(raw instanceof Map)) return;

        Map<String, Object> formData = (Map<String, Object>) raw;
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            if (entry.getValue() instanceof String val) {
                if (isSensitive(entry.getKey(), val)) {
                    entry.setValue(ENC_PREFIX + sm4.encryptBase64(val));
                }
            }
        }
    }

    /**
     * 解密返回给前端的 formData 中的敏感字段
     * 在 WarmFlowService.load()/hisLoad() 返回前调用
     */
    @SuppressWarnings("unchecked")
    public static void decryptData(Object data) {
        if (data == null || sm4 == null) return;
        if (!(data instanceof Map)) return;

        Map<String, Object> formData = (Map<String, Object>) data;
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            if (entry.getValue() instanceof String val) {
                if (val.startsWith(ENC_PREFIX)) {
                    String cipher = val.substring(ENC_PREFIX.length());
                    entry.setValue(sm4.decryptStr(cipher));
                }
            }
        }
    }
}
```

### 5.2 依赖引入

SM4 需要 `hutool-crypto` 和 `bouncycastle` 两个依赖。在 `warm-flow-plugin-ui/warm-flow-plugin-ui-core/pom.xml` 中添加：

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-crypto</artifactId>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
</dependency>
```

> RuoYi-Vue-Plus 项目中 `ruoyi-common-encrypt` 模块已引入这两个依赖，若 warm-flow 插件作为该项目的子模块，可继承版本管理，无需额外指定版本号。

### 5.3 加密拦截：GlobalListener.finish()

```java
package com.xxx.flow.listener;

import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.dromara.warm.flow.ui.crypto.FormDataSm4Encryptor;
import org.springframework.stereotype.Component;

/**
 * Warm-Flow 全局监听器 — 表单敏感数据加密
 * <p>
 * 在任务完成 (finish) 时，持久化前对 formData 中的敏感字段做 SM4 加密。
 * 加密时机位于 mergeVariable() 之前，因此入库的 variable 已为密文。
 * </p>
 */
@Component
public class FormEncryptGlobalListener implements GlobalListener {

    @Override
    public void finish(ListenerVariable variable) {
        FormDataSm4Encryptor.encryptVariable(variable.getVariable());
    }
}
```

### 5.4 解密拦截：WarmFlowService 改造

修改 `warm-flow-plugin-ui/warm-flow-plugin-ui-core/src/main/java/org/dromara/warm/flow/ui/service/WarmFlowService.java`：

```java
// load() 方法中加一行 (line 602)
public static ApiResult<FlowDto> load(Long taskId) {
    FlowParams flowParams = FlowParams.build();
    FlowDto flowDto = FlowEngine.taskService().load(taskId, flowParams);
    // 返回前端前解密敏感字段
    FormDataSm4Encryptor.decryptData(flowDto.getData());
    return ApiResult.ok(flowDto);
}

// hisLoad() 方法中同样加一行 (line 614)
public static ApiResult<FlowDto> hisLoad(Long hisTaskId) {
    FlowParams flowParams = FlowParams.build();
    FlowDto flowDto = FlowEngine.taskService().hisLoad(hisTaskId, flowParams);
    // 返回前端前解密敏感字段
    FormDataSm4Encryptor.decryptData(flowDto.getData());
    return ApiResult.ok(flowDto);
}
```

### 5.5 SM4 密钥初始化

在 `warm-flow-plugin-ui-sb-web` 的 `WarmFlowUiConfig` 中注册初始化：

```java
@Value("${warm-flow.form-encrypt.sm4-key}")
private String sm4Key;

@PostConstruct
public void initSm4() {
    if (StringUtils.hasText(sm4Key)) {
        FormDataSm4Encryptor.init(sm4Key);
    }
}
```

`application.yml` 配置：

```yaml
warm-flow:
  form-encrypt:
    # SM4 密钥，16 字节（128 bit）。生产环境建议通过环境变量注入
    sm4-key: ${WARM_FORM_SM4_KEY:}
```

> **密钥管理**：生产环境**禁止**硬编码密钥。建议通过环境变量 `WARM_FORM_SM4_KEY` 注入，或使用配置中心（如 Nacos）。

## 6. 数据流变化对比

### 加密后写入链路

```
formData {"name":"张三","phone":"13800138000","idCard":"110101199001011234"}
                                            │
                                            │ GlobalListener.finish() 拦截
                                            │ FormDataSm4Encryptor.encryptVariable()
                                            ▼
formData {"name":"张三","phone":"ENC:SM4:xxxx...","idCard":"ENC:SM4:yyyy..."}
                                            │
                                            │ mergeVariable() 持久化
                                            ▼
flow_instance.variable 中 phone/idCard 字段为密文
```

### 加密后读取链路

```
flow_instance.variable 中 phone/idCard 字段为密文
                                            │
                                            │ TaskServiceImpl.load()
                                            ▼
FlowDto.data: {"name":"张三","phone":"ENC:SM4:xxxx...","idCard":"ENC:SM4:yyyy..."}
                                            │
                                            │ FormDataSm4Encryptor.decryptData()
                                            ▼
前端收到: {"name":"张三","phone":"13800138000","idCard":"110101199001011234"}
```

## 7. 历史数据迁移

上线前需要对存量明文数据进行一次性加密。

### 7.1 迁移脚本（Java）

```java
/**
 * 一次性迁移：将存量 flow_instance.variable 和 flow_his_task.variable 中
 * 的 formData 敏感字段加密。执行前需备份数据库。
 */
public void migrateHistoryData() {
    FormDataSm4Encryptor.init(sm4Key);

    // 迁移 flow_instance
    List<Instance> instances = FlowEngine.insService().list();
    for (Instance ins : instances) {
        Map<String, Object> variableMap = ins.getVariableMap();
        FormDataSm4Encryptor.encryptVariable(variableMap);
        ins.setVariable(FlowEngine.jsonConvert.objToStr(variableMap));
        FlowEngine.insService().updateById(ins);
    }

    // 迁移 flow_his_task
    List<HisTask> hisTasks = FlowEngine.hisTaskService().list();
    for (HisTask his : hisTasks) {
        Map<String, Object> variableMap = his.getVariableMap();
        FormDataSm4Encryptor.encryptVariable(variableMap);
        his.setVariable(FlowEngine.jsonConvert.objToStr(variableMap));
        FlowEngine.hisTaskService().updateById(his);
    }
}
```

### 7.2 迁移后验证

```sql
-- 检查是否还有明文手机号（应为 0 条）
SELECT COUNT(*) FROM flow_instance
WHERE variable REGEXP '"phone":\\s*"1[3-9][0-9]{9}"'
   OR variable REGEXP '"mobile":\\s*"1[3-9][0-9]{9}"';

-- 检查身份证号明文
SELECT COUNT(*) FROM flow_instance
WHERE variable REGEXP '"idCard":\\s*"[0-9]{17}[0-9Xx]"';
```

## 8. 风险评估与缓解

### 8.1 条件表达式影响

| 影响程度 | 场景 | 缓解措施 |
|---------|------|---------|
| **低** | 条件表达式引用加密字段做分支判断（如 `${formData.phone == 'xxx'}`） | 几乎不会发生，手机号/身份证号不作为流程分支条件 |
| **无** | 条件表达式引用非敏感字段（如 `${formData.leaveDays > 2}`） | 不受影响 |

### 8.2 性能影响

| 操作 | 额外开销 | 评估 |
|------|---------|------|
| 写入时加密 | 遍历 formData keys + SM4 加密少量字段 | 单次审批约 0.1-0.5ms，可忽略 |
| 读取时解密 | 同上，解密性能与加密相当 | 可忽略 |
| SM4 实例 | 全局单例复用 | 无额外开销 |

### 8.3 密钥丢失风险

密钥丢失将导致**历史加密数据永久无法解密**，审批历史中的敏感字段变为"ENC:SM4:"前缀的密文。

缓解措施：
- 密钥纳入密钥管理系统（如 KMS、Vault）
- 密钥备份，多副本存储
- 密钥轮换时保留历史密钥用于解密旧数据

### 8.4 加密字段被意外展示

在以下场景中密文可能被前端直接展示：
- 不通过 `WarmFlowService.load()` 而是直接查询 `flow_instance` 表
- 第三方系统调用接口未经过解密拦截

缓解措施：
- 所有读取 flow_instance.variable 的接口都必须经过 `FormDataSm4Encryptor.decryptData()` 
- 前端对 `"ENC:SM4:"` 前缀做展示兜底（显示为 "***"）

## 9. 测试方案

### 9.1 单元测试

| 测试用例 | 输入 | 预期输出 |
|---------|------|---------|
| 手机号加密 | `{phone: "13800138000"}` | `{phone: "ENC:SM4:..."}` |
| 身份证加密 | `{idCard: "110101199001011234"}` | `{idCard: "ENC:SM4:..."}` |
| 普通字段不加密 | `{name: "张三"}` | `{name: "张三"}` |
| 防重复加密 | `{phone: "ENC:SM4:..."}` | 不变 |
| 解密 | `{phone: "ENC:SM4:xxxx"}` | `{phone: "13800138000"}` |
| 空值处理 | `{phone: ""}` | `{phone: ""}` |
| 非字符串值 | `{age: 25}` | `{age: 25}` |
| 值正则兜底（字段名不在词库） | `{contact: "13800138000"}` | `{contact: "ENC:SM4:..."}` |
| 15 位身份证 | `{oldCard: "110101900101123"}` | `{oldCard: "ENC:SM4:..."}` |

### 9.2 集成测试

| 测试场景 | 验证点 |
|---------|-------|
| 完整提交流程 | POST `/execute/handle` → 检查 `flow_instance.variable` 中敏感字段为密文 |
| 完整加载流程 | GET `/execute/load/{taskId}` → 检查返回的 formData 中敏感字段为明文 |
| 历史加载 | GET `/execute/hisLoad/{taskId}` → 同上 |
| 条件表达式 | 网关分支条件引用非敏感字段，分支判断正确 |
| 审批轨迹 | HisTask 中敏感字段为密文，`hisLoad` 时正确解密 |

## 10. 附录

### 10.1 关键代码文件索引

| 文件 | 说明 |
|------|------|
| `TaskServiceImpl.java:567-597` | `setInsFinishInfo()` + `mergeVariable()` — 变量持久化 |
| `TaskServiceImpl.java:994-1016` | `load()` — 表单数据加载 |
| `TaskServiceImpl.java:1018-1041` | `hisLoad()` — 历史表单加载 |
| `FlowParams.java:327-333` | `formData()` — 将 formData 写入 variable |
| `WarmFlowService.java:599-603` | `load()` — 对外暴露的 load 接口 |
| `WarmFlowService.java:611-615` | `hisLoad()` — 对外暴露的 hisLoad 接口 |
| `WarmFlowService.java:627-637` | `handle()` — 审批提交入口 |
| `GlobalListener.java` | 全局监听器，加密拦截点 |
| `Listener.java:50` | `LISTENER_FORM_LOAD` 常量 |
| `FlowCons.java:77` | `FORM_DATA = "formData"` 常量 |

### 10.2 RuoYi-Vue-Plus 已有 SM4 基础

| 文件 | 说明 |
|------|------|
| `ruoyi-common-encrypt/src/main/java/.../Sm4Encryptor.java` | SM4 加密器实现 |
| `ruoyi-common-encrypt/src/main/java/.../EncryptUtils.java` | SM4 工具方法 (`encryptBySm4`, `decryptBySm4`) |
| `ruoyi-common-encrypt/src/main/java/.../EncryptField.java` | `@EncryptField` 注解，支持 SM4 |
| `ruoyi-common-encrypt/src/main/java/.../MybatisEncryptInterceptor.java` | MyBatis 数据库字段级加密拦截器 |
| `ruoyi-common-encrypt/pom.xml` | 依赖：`hutool-crypto` + `bcprov-jdk18on` |

> 注意：`MybatisEncryptInterceptor` 作用于实体字段，适用于常规 CRUD 表。本方案处理的是 `flow_instance.variable` 这个 JSON TEXT 列内部的子字段，因此**不能直接用 MybatisEncryptInterceptor**，必须通过本文档描述的 Listener + Service 拦截方案处理。

### 10.3 部署配置清单

- [ ] `hutool-crypto` 依赖引入
- [ ] `bcprov-jdk18on` 依赖引入
- [ ] `application.yml` 配置 SM4 密钥（环境变量注入）
- [ ] `FormDataSm4Encryptor` 工具类创建
- [ ] `FormEncryptGlobalListener` 监听器实现
- [ ] `WarmFlowService.load()` / `hisLoad()` 加解密行
- [ ] `WarmFlowUiConfig` 中初始化 SM4
- [ ] 历史数据迁移脚本
- [ ] 单元测试
- [ ] 集成测试
