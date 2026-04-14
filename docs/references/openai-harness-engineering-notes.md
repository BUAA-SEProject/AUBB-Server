# OpenAI Harness Engineering 笔记

来源：[Harness Engineering](https://openai.com/zh-Hans-CN/index/harness-engineering/)

## 在本项目中的应用方式

- 为代理建立一组小而稳定的顶层文档入口
- 将执行计划视为仓库资产
- 为支撑结构本身增加可执行验证
- 让知识库保持渐进式可发现，而不是把所有上下文塞进一个文件
- 把主观规则转化为书面化、可重复执行的约束

## 针对本项目的解释

对于 AUBB-Server 而言，harness engineering 的含义是：即便真实业务模块尚未出现，后端也应该能仅凭仓库本身被理解、被验证。
