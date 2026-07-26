# Contributing

感谢你参与 DarkRoomLibrary。

## 开始之前

1. 先在 Issues 中说明准备修复的问题或新增功能。
2. 不要提交密码、令牌、邮箱授权码、私钥、真实用户数据或上传文件。
3. 新增行为时请补充相应测试。

## 本地检查

后端：

```powershell
cd backend/dark-room-library-api
mvn clean test
```

前端：

```powershell
cd frontend/dark-room-library-web
npm ci
npm run lint
npm run test:unit
npm run build
```

涉及跨角色流程、库存、预约、采购物流或浏览器行为的修改，还应运行
`tests/e2e` 下对应的真实服务脚本，并在 Pull Request 中记录使用的数据库、
中间件状态和测试结果。

## 提交 Pull Request

- 保持一次 Pull Request 只处理一个主题。
- 在说明中写清修改原因、测试结果和可能的兼容性影响。
- 界面改动请附截图；数据库结构改动请同步更新初始化脚本与文档。
- 不要添加项目级许可证或完整原创声明，除非教学脚手架的授权问题已经得到可验证的解决。
