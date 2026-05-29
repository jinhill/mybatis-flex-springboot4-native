## 项目说明
springboot v4.0.6 + mybatis-flex v1.11.7 + graalvm25 编译native image 示例

### 创建数据库表
```sql
-- 1. 创建数据库（指定字符集，建议 utf8mb4）
CREATE DATABASE IF NOT EXISTS `dbtest`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- 2. 创建用户（允许任何主机连接，生产环境可改为 localhost）
CREATE USER IF NOT EXISTS 'dbtest'@'%' IDENTIFIED BY 'dbtest';
-- 如果只允许本地连接，使用：CREATE USER IF NOT EXISTS 'dbtest'@'localhost' IDENTIFIED BY 'dbtest';

-- 3. 授权 dbtest 用户对 dbtest 数据库的所有权限
GRANT ALL PRIVILEGES ON `dbtest`.* TO 'dbtest'@'%';
-- 若用户是 localhost 主机，则对应的授权：GRANT ALL PRIVILEGES ON `dbtest`.* TO 'dbtest'@'localhost';

-- 4. 刷新权限使生效
FLUSH PRIVILEGES;

-- 5. 切换到 dbtest 数据库（或者后续建表时使用 dbtest.表名）
USE `dbtest`;

-- 6. 创建表
CREATE TABLE IF NOT EXISTS `tb_account`
(
    `id`        INTEGER PRIMARY KEY AUTO_INCREMENT,
    `user_name` VARCHAR(100),
    `age`       INTEGER,
    `birthday`  DATETIME
);

-- 7. 插入数据
INSERT INTO `tb_account` (`id`, `user_name`, `age`, `birthday`)
VALUES (1, '张三', 18, '2020-01-11'),
       (2, '李四', 19, '2021-03-21');
```
### 编译native image 示例  
```sh
# 编译jar包
mvn clean package

# 生成反射配置文件
java -Dfile.encoding=utf-8 -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image -jar target/mybatisflex-0.0.1-SNAPSHOT.jar

# 编译native image
mvn -Pnative clean native:compile

# 运行native image
./target/mybatisflex
```