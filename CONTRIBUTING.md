# 贡献指南

MiniSpringBoot 是一个教学向的 Spring Boot 机制复刻。框架内核保持零第三方运行时依赖；
React、MySQL、HikariCP、Nginx 等真实依赖只属于 demo 与部署验证轨道。M0–M10 已完成，
现阶段优先接受可复现缺陷、安全修复、依赖维护、测试增强和文档订正。

## 提交前先确认边界

- 不把教学子集描述成生产级 Spring Boot 替代品。
- 不为 demo 便利把第三方运行时依赖倒灌进框架内核。
- 不把本机自验证、导入证据复验或单机三实例演练扩大成未经证明的生产结论。
- 新增框架能力前，先说明教学收益、兼容影响、失败路径和不实现的边界。
- 不提交凭据、私有日志、代理配置、机器专属绝对路径或本地视觉验收截图集。

## 本地验证

后端与框架模块：

```powershell
mvn clean install
```

前端：

```powershell
Set-Location demo-frontend
npm ci --registry=https://registry.npmjs.org
npm run build
npm audit --audit-level=moderate --registry=https://registry.npmjs.org
```

涉及 M10 拓扑、容量、故障切换、事务或就绪语义时，还应按
[`docs/10-high-availability.md`](docs/10-high-availability.md) 运行对应的有界验证，并明确哪些步骤没有执行。

## Pull Request 要求

- 一次 PR 只解决一个边界清晰的问题。
- 给出复现步骤、实际执行的验证以及未执行项。
- 代码行为、测试、README、路线图与 Release 坐标必须一致。
- 修复缺陷时补充能阻止回归的测试；纯文档修订说明事实来源。
- 视觉改动至少检查一个桌面视口和一个窄屏视口，并检查控制台与横向溢出。

安全问题不要创建公开 Issue，请按 [`SECURITY.md`](SECURITY.md) 私下报告。
