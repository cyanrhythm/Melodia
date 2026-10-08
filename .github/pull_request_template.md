<!--
请按改动类型选择专用模板，在创建 PR 的地址后追加参数即可：
  ?template=feature.md   功能
  ?template=fix.md       修复
  ?template=refactor.md  重构
其余情况（文档、构建、依赖等）直接填写下面的内容。
-->

## 概述

<!-- 做了什么、为什么做；关联 Issue 用 Closes #编号 -->

## 改动

-

## 测试与验证

- [ ] `./gradlew :app:testDebugUnitTest :shared:desktopTest`
- [ ] `./gradlew :desktopApp:compileKotlin`
- [ ] `./gradlew :app:assembleRelease`

## 风险与影响范围

<!-- 没有则写“无” -->

## 自检清单

- [ ] 本 PR 只做一类事情，没有混入无关改动
- [ ] 没有新增第三方依赖
- [ ] 没有提交临时文件、调试代码、密钥或签名文件
