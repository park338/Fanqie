# Fanqie

单用户本地番茄钟 Web 应用，后端为 Spring Boot + MySQL，前端为 React + Vite。内置“小茄”智能体，可基于任务、番茄记录和今日安排生成时间管理建议与计划草稿。

## 本地启动

1. 创建数据库：

```powershell
Get-Content init-mysql.sql | mysql -uroot -p
```

2. 启动后端：

```powershell
$env:FANQIE_DB_USERNAME="root"
$env:FANQIE_DB_PASSWORD="你的 MySQL 密码"
$env:DEEPSEEK_API_KEY="你的 DeepSeek Key"
mvn spring-boot:run
```

3. 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址为 `http://127.0.0.1:5173`，后端默认地址为 `http://127.0.0.1:8080`。

## 验证

```powershell
$env:FANQIE_DB_USERNAME="root"
$env:FANQIE_DB_PASSWORD="你的 MySQL 密码"
$env:FANQIE_DB_URL="jdbc:mysql://localhost:3306/fanqie_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
mvn test

cd frontend
npm test
npm run build
```

如需验收完整链路，可启动前后端后在浏览器中手动验证“创建任务 -> 完成番茄 -> 小茄生成计划 -> 确认加入今日安排”。
