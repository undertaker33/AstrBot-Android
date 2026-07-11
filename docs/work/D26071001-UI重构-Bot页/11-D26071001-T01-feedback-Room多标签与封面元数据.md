# D26071001-T01 Room 多标签与封面元数据 Feedback

更新时间：2026-07-11 +08:00

## 状态

- Scene：`uth-dev`
- Mode：`todo-implementation`
- 结果：完成；Design 尚未完成，下一项为 T02。
- Git：未执行任何 Git 写入。

## 实现

- `PersonaProfile` 改以规范化、有序、最多三项的 `tags` 为真源；增加 `PersonaCropSpec`、`PersonaCoverMetadata` 及有限值、中心范围、正数缩放、相对资产引用和像素尺寸校验。
- 增加 `persona_tags`、`persona_cover_assets` Room entity，并纳入 aggregate、write model、DAO `replaceAll` 事务与外键级联。
- 增加 23→24 migration：完整拆分半角/全角逗号旧标签，过滤空项、稳定去重后取前三项；重建 `persona_profiles` 去除 `tag`，并保留 prompt/tool 子表内容。
- mapper、repository normalization、legacy JSON import 和 port adapter 已统一使用 `tags` 与 cover metadata；API 内保留旧 presentation 的只读/构造兼容 seam，但不形成 Room 双真源。
- Room 版本升级至 24；`app/schemas` 与 `core/db/schemas` 的 24 schema SHA-256 均为 `39B2F4ADDBBFB05A0F84A75A30FE6AF7277D7ED70750857F321AA1A50BAA2B52`。

## 变更文件

- Persona API/data：`PersonaProfile.kt`、`PersonaMappers.kt`、`FeaturePersonaRepository.kt`、`FeaturePersonaRepositoryPortAdapter.kt`、`LegacyPersonaImport.kt`、data 测试及其 JUnit test 配置。
- Core DB：`PersonaEntity.kt`、`PersonaTagEntity.kt`、`PersonaCoverAssetEntity.kt`、`PersonaAggregate.kt`、`PersonaAggregateDao.kt`、`ElymBotDatabase.kt`、`ElymBotDatabaseMigrations.kt`、`DbMigrations.kt`、数据库合同测试与两处 24 schema。
- 直接相关测试 fixture：`PersonaMappersTest.kt`、`PluginRuntimeCompatRepositoryHarness.kt`。

## RED / GREEN

- RED：先将数据库终点断言改为 24，并新增 tags normalization、crop validation、mapper round-trip 测试；首次执行因 migration 终点仍为 23 失败，随后测试编译暴露缺失 JUnit 配置。
- GREEN：补齐最小实现及测试配置后，core DB 与 persona data 窄测试通过；最终四条验证统一通过。

## 验证

- `sqlite3.exe :memory:` migration 行为验证：异常旧值 `,A，A,B,,C,D` 得到 `A/B/C`，空标签为 0 条，prompt/tool 保留，旧 `tag` 列为 0，删除 Persona 后 tags/cover/prompt/tool 子记录合计为 0。
- `./gradlew.bat :core:db:testDebugUnitTest :feature:persona:data:testDebugUnitTest modulePersonaCheck architectureCheck --console=plain --no-daemon --stacktrace`：`BUILD SUCCESSFUL`（2026-07-11，新鲜执行）。
- 输出扫描：warning `0`，exception `0`，failed marker `0`。

## 风险、未验证项与回滚

- 未验证项：本 Todo 不处理图片文件生命周期、备份或 UI，这些分别属于 T02–T05；未运行真实设备 migration instrumentation，迁移 SQL 已通过本机 SQLite 内存库行为验证及 Room schema/模块/架构验证。
- 风险：API 兼容 seam 仅用于尚未进入 T04/T05 的旧 presentation；后续 UI Todo 应迁到 `tags` 后移除 seam。
- 回滚：整体回退本 Feedback 所列代码、测试和 24 schema，并恢复数据库版本 23；已迁移到 24 的真实数据库不可直接降级，需使用正式恢复/重建策略。
