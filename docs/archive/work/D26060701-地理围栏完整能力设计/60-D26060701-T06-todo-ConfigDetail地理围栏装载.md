# D26060701-T06 ConfigDetail 地理围栏装载 Todo

更新时间：2026-06-07 00:58 +08:00

## 场景

- Scene：`uth-dev`
- Mode：`todo-breakdown`
- 来源 Design：`docs/work/D26060701-地理围栏完整能力设计/00-D26060701-design.md`
- 前置 Todo：`T01 模块与数据真源基线`、`T04 我的页地理围栏配置 UI`
- 执行方式：待用户决定；本轮不写 worker prompt，不实现代码。

## 目标

在“配置-配置详情页-平台设置”中新增“地理围栏”项，用户通过弹窗从“我的-地理围栏配置”真源中选择规则并装载到当前 ConfigProfile。ConfigDetail 只保存 binding，不编辑或复制规则内容。

## 已确认约束

- “我的-地理围栏配置”是唯一真源。
- ConfigProfile 不新增坐标、半径或动作 prompt 字段。
- ConfigDetail 不直接访问 geofence DAO。
- 装载变化应触发 runtime reconciliation，但不在 UI 内直接操作 GeofencingClient。

## 非目标

- 不实现地理围栏规则创建/编辑；那属于“我的-地理围栏配置”页面。
- 不实现地图选择器。
- 不实现 Agent 创建工具。
- 不修改 Resource Center projection 语义来承载 geofence。

## 允许修改范围

- `feature/config/presentation/src/main/java/**`
- `feature/geofence/api/**`
- `feature/geofence/presentation/**`
- `app-integration/src/main/java/com/elymbot/android/app/integration/geofence/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `app/src/test/java/com/elymbot/android/ui/config/**`
- `app/src/test/java/com/elymbot/android/feature/geofence/**`
- `app/src/test/java/com/elymbot/android/architecture/**`

## 必须实现

1. 新增 Config section
   - `ConfigSection.Geofence`
   - 加入 `config_nav_group_platform`。
   - Section 标题为 `地理围栏`。
   - 显示当前装载数量和前 2 条规则摘要。

2. 新增管理弹窗
   - 参考白名单的“管理”弹窗入口风格。
   - 使用下拉框或多选列表选择已有 geofence rules。
   - 每条 binding 可在当前 config 下启用/禁用。
   - 提供跳转“我的-地理围栏配置”的入口。

3. 新增 binding controller
   - 从 geofence repository port 读取规则和当前 config bindings。
   - 保存时只写 `config_geofence_bindings`。
   - 保存后通过 runtime reconciliation port 通知重注册。

4. 保存与退出
   - ConfigDetail unsaved-change 逻辑要包含 geofence binding 草稿，或该 section 独立即时保存；必须二选一并测试固定。
   - 推荐独立即时保存，避免把 geofence binding 塞进 ConfigProfile draft。

5. 空状态
   - 没有 geofence rule 时显示“先去我的-地理围栏配置创建”。
   - 不允许在 ConfigDetail 内新建坐标和半径。

## 必须补测试

- `ConfigGeofenceBindingPresentationTest`
  - 无规则时显示空状态和跳转入口。
  - 有规则时可多选装载。
  - 保存只写 binding。
  - 禁用当前 config 下某条 binding 不修改 rule 本体。

- `ConfigDetailGeofenceSectionTest`
  - 平台设置抽屉包含“地理围栏”。
  - Section 显示装载数量和摘要。
  - 管理弹窗可打开。

- architecture/source contract
  - Config presentation 不 import geofence data DAO。
  - ConfigProfile 不新增 geofence 坐标/半径字段。
  - Resource Center projection 不被复用承载 geofence binding。

## 验证命令

```powershell
.\gradlew.bat moduleConfigCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleGeofenceCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Config*Geofence*" --tests "*Geofence*Binding*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

## 完成判定

- ConfigDetail 能装载已有地理围栏规则。
- 装载关系只保存在 `config_geofence_bindings`。
- 地理围栏规则内容仍只能从“我的-地理围栏配置”维护。
