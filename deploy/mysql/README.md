# 数据库初始化

Docker 只负责本路径的 MySQL，Java demo 与前端由宿主机运行。唯一建表入口为
[init.sql](init.sql)，包含 `users`、`accounts` 与两条转账种子数据；Java 没有自动建表或迁移机制。

## Docker 路径

从仓库根目录执行 `docker compose -f deploy/mysql/docker-compose.yml up -d`。
Compose 首次创建空数据卷时先建立 `minispring_demo` 和应用账号，再自动导入 `init.sql`；
宿主端口为 `13306`。重启和重新构建不会导入已有卷，删除数据卷会丢失业务数据。

## 本机 MySQL 路径

也可使用本机 MySQL 8，先用管理员在独立、全新的演示库中建立库与应用账号：

```sql
CREATE DATABASE minispring_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'minispring'@'localhost' IDENTIFIED BY 'minispring_123';
GRANT ALL PRIVILEGES ON minispring_demo.* TO 'minispring'@'localhost';
```

以上为本机演示凭据；自定义账号需同步应用配置。从仓库根目录导入结构：

```powershell
cmd /c "mysql --default-character-set=utf8mb4 -h localhost -P 3306 -u minispring -p minispring_demo < deploy\mysql\init.sql"
```

`init.sql` 需要库与账号预先存在；种子 INSERT 不可重复执行，不用于已有库升级或重置余额。
使用本机 `3306` 时，先安装模块再覆盖 demo 的数据源地址：

```powershell
mvn -DskipTests install
mvn -pl mini-spring-demo exec:java "-Dexec.mainClass=com.minispring.demo.app.DemoApplication" "-Dminispring.datasource.url=jdbc:mysql://localhost:3306/minispring_demo?useSSL=false&allowPublicKeyRetrieval=true"
```

这条本机路径跳过测试，不构成测试通过证据。JDBC 单测固定连接 `13306`；运行全量
`mvn clean install` 时应使用 Docker 路径，或提供同地址、端口和演示账号的独立 MySQL。
