-- 이 프로젝트를 처음 받으면 제일 먼저 실행하세요.
-- MySQL에 접속한 상태(mysql> 프롬프트)에서 전체를 복사해서 붙여넣으면 됩니다.

CREATE DATABASE IF NOT EXISTS cex;
USE cex;

CREATE TABLE IF NOT EXISTS account (
    account_id BIGINT PRIMARY KEY,
    name       VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS balance (
    account_id BIGINT      NOT NULL,
    asset      VARCHAR(10) NOT NULL,
    available  BIGINT      NOT NULL DEFAULT 0,
    locked     BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, asset),
    CONSTRAINT chk_bal_nonneg CHECK (available >= 0 AND locked >= 0)
);

CREATE TABLE IF NOT EXISTS orders (
    order_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT      NOT NULL,
    symbol     VARCHAR(20) NOT NULL,
    side       VARCHAR(4)  NOT NULL,
    price      BIGINT      NOT NULL,
    qty        BIGINT      NOT NULL,
    filled_qty BIGINT      NOT NULL DEFAULT 0,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS trade (
    trade_id      BIGINT PRIMARY KEY,   -- 엔진이 발급한 값 그대로 씀. AUTO_INCREMENT 아님
    symbol        VARCHAR(20) NOT NULL,
    buy_order_id  BIGINT      NOT NULL,
    sell_order_id BIGINT      NOT NULL,
    price         BIGINT      NOT NULL,
    qty           BIGINT      NOT NULL,
    taker_side    VARCHAR(4)  NOT NULL,
    ts_ms         BIGINT      NOT NULL
);

-- 여기까지 에러 없이 실행됐으면 준비 끝. 아래로 확인만 해보세요.
SHOW TABLES;