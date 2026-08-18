-- MiniSpringBoot M8 建表脚本（容器首启自动执行）
USE minispring_demo;

-- 用户表：对齐 demo 的 User（id 自增主键 → V2 自增回填验收）
CREATE TABLE IF NOT EXISTS users (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    name  VARCHAR(64)  NOT NULL,
    email VARCHAR(128)          DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 账户表：转账场景（V3 事务回滚 / V4 提交 / V5 无脏读）
CREATE TABLE IF NOT EXISTS accounts (
    id      BIGINT         NOT NULL,
    balance DECIMAL(12, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 转账验收初始数据：A=1000，B=1000
INSERT INTO accounts (id, balance) VALUES (1, 1000.00), (2, 1000.00);
