# lucentblock_hw

루센트블록 과제 — 미니 거래소(CEX). 심볼 `BTCKRW`, 가격·수량은 전부 정수.

## 구성

| 폴더 | 역할 | 실행 |
|---|---|---|
| `engine/` | 매칭 엔진 (Rust) | `cargo run` — :9000에서 주문 수신, 체결 시 :8080으로 이벤트 발행 |
| `server/` | API 서버 (Spring Boot, :8080) | `application-example.yml`을 `application.yml`로 복사 후 MySQL 비번 채우고 실행 |
| `frontend/` | 웹 프론트 (React + Vite) | `npm install && npm run dev` |

## 실행 순서

1. **엔진** — `cd engine && cargo run` (터미널에서 `book`/`trades` 치면 오더북·체결 확인 가능)
2. **서버** — 엔진보다 늦게 켜야 주문 API가 503으로 떨어지지 않는다
3. **프론트**

## 연동 규약 (JSON snake_case)

- 서버 → 엔진: `POST :9000/engine/orders` `{order_id, symbol, side("BUY"/"SELL"), price, qty, ts_ms}` → `{"accepted":bool}`
- 서버 → 엔진: `POST :9000/engine/orders/cancel` `{order_id}` → `{"canceled":bool}`
- 엔진 → 서버: `POST :8080/internal/trades` `{trade_id, symbol, buy_order_id, sell_order_id, price, qty, taker_side, ts_ms}` — trade_id는 1부터 증가, 서버가 중복 제거
