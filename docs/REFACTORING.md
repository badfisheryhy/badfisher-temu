# 改造进度与验收

本项目仅提供后端代码与接口，**没有前端页面、前端项目或前端演示数据**。

来源工程：lenkor-temu，读取时 HEAD 为 `94fb4cbb7cbba972c1ee699b6e1f7666a5f36882`。
导入以读取到的工作区文件为准；原目录的 Git 状态读取受权限限制，不能将该 HEAD 视为导入内容的精确快照。

## 实施阶段

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| 1 | 独立工程、公开依赖、凭证隔离、Java 8 编译 | 已完成基线验收 |
| 2 | 四模块、业务分包、测试迁移、客户端解耦 | 已完成结构验收；大型业务服务拆分另列后续 |
| 3 | 店铺级业务身份约定 | 保留业务维护的 `channelId`，与 `shopId` 同一维度；见 README |
| 4 | 执行批次标识、任务幂等、失败反馈、数据库验证 | 已实现执行身份与租约保护；任务创建幂等、失败汇总及真实数据库验收待完成 |
| 5 | 独立运行、接口鉴权、部署示例、模拟链路验收 | H2 启动与鉴权已验证；容器、MySQL 及完整同步链路待验证 |
| 6 | CI、发布说明、许可证与版本维护策略 | 已配置 CI；未运行远端 CI，公开发布与许可证待定 |

## 保留与调整

- 保留订单、售后、金额、商品、库存、仓库业务及原有 38 个测试类。
- 不导入原配置文件、代码生成器数据库配置、凭证初始化 SQL、运行日志或 Git 历史。
- 补回业务实际使用的两张金额表建表语句。
- 客户端通过凭证接口、限流接口与日志接口连接业务模块，不直接依赖数据库 Entity。
- 请求及响应日志执行脱敏；无法解析或过长的正文省略。
- 授权 Entity 的 `toString` 排除凭证字段。
- HTTP 接口使用独立 API Key；XXL-JOB 默认不启动。
- 通用平台调用调试接口默认不注册，须显式开启。
- 恢复原测试中被注释的 Mock 数据，显式匹配 MyBatis Wrapper 重载；原 JUnit 4 枚举测试迁移到 JUnit 5。
- 日志清理保留原实现的一个月行为，修正原测试和注释中的三个月描述，并增加保留月数配置。

## 初始基线验证记录（2026-10-08，框架升级前）

- JDK：`1.8.0_202`，由测试报告中的实际 JVM 属性确认。
- 使用仅指向 Maven Central 的隔离 settings 下载依赖；不依赖原有公司仓库。
- `mvn clean verify` 已通过，实际执行时使用隔离 settings 和工程内缓存；随后离线构建也通过。
- 共发现 147 个测试：100 个通过，47 个真实平台/外部环境测试按默认开关跳过，失败 0，错误 0。
- H2 MySQL 模式执行 18 张表的建表结构，并验证 Spring 启动、Mapper 扫描、分页、401 鉴权、凭证不出现在响应及调试接口默认关闭。测试仅转换数据库选择、存储引擎和索引名称作用域差异，不代表 MySQL 实例验收。
- 18 张表与持久化 Entity 字段的静态比对无缺失字段。
- 检查未发现原初始化 SQL 中的已知凭证进入新源码；导入的 172 个原 Java 源文件哈希未发生变化。
- Git Diff 检查通过；构建产物为 `temu-server/target/badfisher-temu.jar`。
- 保留的旧测试仍有弃用 API 和泛型 unchecked 编译提示；未通过关闭编译器检查掩盖提示。

未执行真实 MySQL、Redis、Temu API、XXL-JOB Admin、Docker 或远端 GitHub Actions 验收。

## Java 8 框架升级（2026-10-08）

| 项目 | 升级前 | 升级后 |
| --- | --- | --- |
| JDK | Java 8 | Java 8，保持不变 |
| Spring Boot | 2.3.2.RELEASE | 2.7.18 |
| XXL-JOB Core | 2.2.0 | 2.5.0 |
| MySQL 驱动坐标 | `mysql:mysql-connector-java` | `com.mysql:mysql-connector-j`，版本由 Boot 管理 |

版本选择依据：[Spring Boot 2.7.18 官方系统要求](https://github.com/spring-projects/spring-boot/blob/v2.7.18/spring-boot-project/spring-boot-docs/src/docs/asciidoc/getting-started/system-requirements.adoc)明确支持 Java 8；[XXL-JOB 2.5.0 官方 POM](https://github.com/xuxueli/xxl-job/blob/2.5.0/pom.xml)使用 Java 8 和 Spring Boot 2.7.18，而 [3.0.0 POM](https://github.com/xuxueli/xxl-job/blob/3.0.0/pom.xml)要求 Java 17。Spring Boot 2.7.18 已结束开源维护，当前选型不承诺持续的开源安全更新。

- 26 个 `@XxlJob` 入口迁移为无参 `void` 方法，保留全部 Handler 名称与业务参数格式。
- 参数使用 `XxlJobHelper.getJobParam()`；统一结果适配器将成功、异常和补偿任务的显式失败写入执行上下文。
- Spring Security 改用 `SecurityFilterChain` Bean，保留 API Key 与匿名健康检查的原有接口契约。
- 旧 `okhttp.version` 与 Boot 2.7 引入的 OkHttp BOM 属性同名，首次构建尝试解析不存在的 `okhttp-bom:3.14.9`。已改为项目专用的 `temu.okhttp.version`，保留现有 OkHttp 客户端版本。
- Spring、Security、Jackson、MySQL 驱动、H2 等传递依赖随 Boot 依赖管理更新；未单独改动 MyBatis-Plus、Redisson 和 Hutool 的版本声明。
- XXL-JOB Admin 未在本次工作中部署或升级，接入目标为 2.5.0，仍需独立调度中心联调。

升级验证：使用实际 `1.8.0_202` JVM 执行全模块 `clean verify`，共 153 个测试，106 个通过、47 个外部联调测试跳过、失败及错误均为 0。检查覆盖 H2 启动与分页、API Key 鉴权、26 个 Handler 的真实执行器注册，以及通过 `MethodJobHandler` 调用验证上下文参数、显式失败、异常与成功结果。Git Diff 检查通过。

上述验证使用了本地隔离 Maven 配置与缓存；源码整理时已移除临时工具、缓存和构建产物。在工程根目录使用 `mvn -B -ntp clean verify` 重新构建，需要配置 Java 8 并能够访问公开 Maven 仓库。

## 待完成的业务调整

业务维护的 `channelId` 是保留的店铺级预留标识，与 `shopId` 同一维度，不安排字段替换或独立店铺模型迁移。
任务已加入执行身份、租约检查及事务写入保护；任务创建幂等和调度失败汇总仍需后续处理。
大型订单、售后服务的进一步职责拆分，以及 Controller 查询下沉，需要逐领域完成并回归。

数据库、Redis 多实例、真实 Temu API 和外部 XXL-JOB Admin 的联调结果必须单独记录，不由单元测试或编译结果推断。
