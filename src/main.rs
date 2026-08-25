//! 매칭 엔진 프로세스. `cargo run`
//!
//! - `POST :9000/engine/orders` 로 서버(Spring)가 보내는 주문을 받아 매칭한다 -> `{"accepted":true}`
//! - 체결이 나면 `POST :8080/internal/trades` 로 체결 이벤트를 발행한다
//! - 터미널에서 `book` / `trades` 를 치면 오더북·체결 내역을 눈으로 확인할 수 있다

use lucent::{start_engine, EngineCommand, OrderType, Price, Qty, Side, Trade};
use serde_json::{json, Value};
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

/// 팀 확정: 슬래시 없는 심볼, 가격·수량은 전부 정수.
const SYMBOL: &str = "BTCKRW";
/// 엔진이 주문을 받는 주소. 서버 EngineClient의 engine.base-url과 맞춘 값.
const LISTEN_ADDR: &str = "127.0.0.1:9000";
/// 체결 이벤트를 보낼 서버 주소.
const SERVER_ADDR: &str = "127.0.0.1:8080";

fn main() {
    let (engine, events) = start_engine();
    thread::spawn(move || publish_trades(events));
    {
        let engine = engine.clone();
        thread::spawn(move || serve(engine));
    }

    println!("매칭 엔진 가동");
    println!("  주문 수신: http://{LISTEN_ADDR}/engine/orders");
    println!("  체결 발행: http://{SERVER_ADDR}/internal/trades");
    println!("  CLI: book | trades | quit");

    for line in std::io::stdin().lock().lines() {
        let Ok(line) = line else { break };
        match line.trim() {
            "" => {}
            "quit" | "exit" => return,
            "book" => {
                let (bids, asks) = ask(&engine, |r| EngineCommand::Book { reply: r });
                println!("  매도: {}", fmt_levels(&asks));
                println!("  매수: {}", fmt_levels(&bids));
            }
            "trades" => {
                for t in ask(&engine, |r| EngineCommand::Trades { reply: r }) {
                    println!("  가격 {} x {} (maker #{} <- taker #{})", t.price, t.qty, t.maker_order_id, t.taker_order_id);
                }
            }
            other => println!("모르는 명령: {other} (book | trades | quit)"),
        }
    }
    // stdin이 닫혀도(백그라운드/서비스 실행) HTTP·발행 스레드는 계속 돌아야 한다.
    loop {
        thread::park();
    }
}

/// 명령 하나 보내고 회신을 기다린다.
fn ask<T>(engine: &Sender<EngineCommand>, make: impl FnOnce(Sender<T>) -> EngineCommand) -> T {
    let (tx, rx) = channel();
    engine.send(make(tx)).expect("엔진 스레드가 죽었다");
    rx.recv().expect("엔진이 응답하지 않는다")
}

fn fmt_levels(levels: &[(Price, Qty)]) -> String {
    if levels.is_empty() {
        return "(비어있음)".into();
    }
    levels.iter().map(|(p, q)| format!("{p}원x{q}")).collect::<Vec<_>>().join(", ")
}

// ---------- 주문 수신 (서버 -> 엔진) ----------

// ponytail: 커넥션 순차 처리 미니 HTTP 서버. 호출자가 로컬 서버 하나뿐이라 충분하고,
// 동시 접속이 필요해지면 tiny_http로 교체.
fn serve(engine: Sender<EngineCommand>) {
    let listener = TcpListener::bind(LISTEN_ADDR).expect("9000 포트 바인딩 실패 (이미 떠 있는지 확인)");
    for stream in listener.incoming().flatten() {
        if let Err(e) = handle_conn(stream, &engine) {
            eprintln!("요청 처리 실패: {e}");
        }
    }
}

fn handle_conn(stream: TcpStream, engine: &Sender<EngineCommand>) -> std::io::Result<()> {
    stream.set_read_timeout(Some(Duration::from_secs(2)))?;
    let mut reader = BufReader::new(&stream);

    let mut request_line = String::new();
    reader.read_line(&mut request_line)?;
    let mut parts = request_line.split_whitespace();
    let method = parts.next().unwrap_or("");
    let path = parts.next().unwrap_or("");

    // 헤더에서 Content-Length만 취하고 빈 줄까지 버린다.
    let mut content_length = 0usize;
    loop {
        let mut line = String::new();
        if reader.read_line(&mut line)? == 0 || line.trim().is_empty() {
            break;
        }
        if let Some(v) = line.to_ascii_lowercase().strip_prefix("content-length:") {
            content_length = v.trim().parse().unwrap_or(0);
        }
    }
    let mut body = vec![0u8; content_length];
    reader.read_exact(&mut body)?;

    let (status, response) = match (method, path) {
        ("POST", "/engine/orders") => {
            let accepted = accept_order(&body, engine);
            (200, json!({ "accepted": accepted }).to_string())
        }
        _ => (404, json!({ "error": "not found" }).to_string()),
    };

    let reason = if status == 200 { "OK" } else { "Not Found" };
    write!(
        &stream,
        "HTTP/1.1 {status} {reason}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{response}",
        response.len()
    )
}

/// 서버가 보낸 주문 JSON을 검증해 엔진에 넣는다. 형식이 조금이라도 어긋나면 거절(false).
/// 서버 쪽 규약: {"order_id", "symbol", "side"("BUY"/"SELL"), "price", "qty", "ts_ms"} 전부 정수/문자열.
fn accept_order(body: &[u8], engine: &Sender<EngineCommand>) -> bool {
    let Ok(v) = serde_json::from_slice::<Value>(body) else { return false };
    if v["symbol"].as_str() != Some(SYMBOL) {
        return false;
    }
    let side = match v["side"].as_str() {
        Some("BUY") => Side::Buy,
        Some("SELL") => Side::Sell,
        _ => return false,
    };
    // as_u64는 음수·소수를 전부 None으로 떨어뜨린다 = "가격·수량 정수" 검증.
    let (Some(order_id), Some(price), Some(qty)) = (v["order_id"].as_u64(), v["price"].as_u64(), v["qty"].as_u64()) else {
        return false;
    };
    if price == 0 || qty == 0 {
        return false;
    }

    let (reply_tx, reply_rx) = channel();
    let sent = engine.send(EngineCommand::Order {
        order_id,
        user_id: 0, // 서버는 account_id를 엔진에 안 보낸다. 정산은 서버 몫.
        side,
        order_type: OrderType::Limit,
        price,
        qty,
        reply: reply_tx,
    });
    // 매칭까지 끝난 뒤 accepted를 돌려준다. 서버 read timeout이 1초지만 매칭은 마이크로초 단위라 여유.
    sent.is_ok() && reply_rx.recv().is_ok()
}

// ---------- 체결 발행 (엔진 -> 서버) ----------

fn publish_trades(events: Receiver<Trade>) {
    // trade_id 발행 규칙: 1부터 중복 없이 증가. 발행 스레드가 하나뿐이라 카운터면 충분.
    let mut trade_id: u64 = 0;
    for t in events {
        trade_id += 1;
        let body = trade_event_body(trade_id, &t, now_ms());
        // 서버가 trade_id로 중복을 걸러주므로(received:true) 성공할 때까지 재전송해도 안전하다.
        // ponytail: 메모리 큐 + 무한 재시도. 엔진 프로세스가 죽으면 미전송분 유실 — WAL 붙일 때 해결.
        loop {
            match post_json(SERVER_ADDR, "/internal/trades", &body) {
                Ok(()) => break,
                Err(e) => {
                    eprintln!("체결 이벤트 전송 실패({e}), 1초 후 재시도: {body}");
                    thread::sleep(Duration::from_secs(1));
                }
            }
        }
        println!("[체결발행] {body}");
    }
}

/// 팀 확정 형식: {trade_id, symbol, buy_order_id, sell_order_id, price, qty, taker_side, ts_ms}
fn trade_event_body(trade_id: u64, t: &Trade, ts_ms: u64) -> String {
    let (buy_order_id, sell_order_id) = match t.taker_side {
        Side::Buy => (t.taker_order_id, t.maker_order_id),
        Side::Sell => (t.maker_order_id, t.taker_order_id),
    };
    json!({
        "trade_id": trade_id,
        "symbol": SYMBOL,
        "buy_order_id": buy_order_id,
        "sell_order_id": sell_order_id,
        "price": t.price,
        "qty": t.qty,
        "taker_side": if t.taker_side == Side::Buy { "BUY" } else { "SELL" },
        "ts_ms": ts_ms,
    })
    .to_string()
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).expect("시계가 1970년 이전").as_millis() as u64
}

fn post_json(addr: &str, path: &str, body: &str) -> std::io::Result<()> {
    let addr: SocketAddr = addr.parse().expect("주소 형식 오류");
    let mut stream = TcpStream::connect_timeout(&addr, Duration::from_secs(1))?;
    stream.set_read_timeout(Some(Duration::from_secs(2)))?;
    write!(
        stream,
        "POST {path} HTTP/1.1\r\nHost: {addr}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    )?;
    let mut status_line = String::new();
    BufReader::new(&stream).read_line(&mut status_line)?;
    if status_line.contains(" 200 ") {
        Ok(())
    } else {
        Err(std::io::Error::other(format!("서버 응답: {}", status_line.trim())))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn depth(engine: &Sender<EngineCommand>) -> (Vec<(Price, Qty)>, Vec<(Price, Qty)>) {
        ask(engine, |r| EngineCommand::Book { reply: r })
    }

    #[test]
    fn server_order_json_enters_the_book() {
        let (engine, _events) = start_engine();

        let ok = accept_order(
            br#"{"order_id":42,"symbol":"BTCKRW","side":"BUY","price":50000,"qty":2,"ts_ms":1}"#,
            &engine,
        );
        assert!(ok);
        let (bids, _) = depth(&engine);
        assert_eq!(bids, vec![(50_000, 2)]); // 실제로 오더북에 들어갔다

        // 형식이 어긋나면 전부 거절: 심볼 다름 / side 이상 / 소수 가격 / 수량 0
        assert!(!accept_order(br#"{"order_id":1,"symbol":"BTC/KRW","side":"BUY","price":1,"qty":1}"#, &engine));
        assert!(!accept_order(br#"{"order_id":1,"symbol":"BTCKRW","side":"LONG","price":1,"qty":1}"#, &engine));
        assert!(!accept_order(br#"{"order_id":1,"symbol":"BTCKRW","side":"BUY","price":1.5,"qty":1}"#, &engine));
        assert!(!accept_order(br#"{"order_id":1,"symbol":"BTCKRW","side":"BUY","price":1,"qty":0}"#, &engine));
        assert!(!accept_order(b"not json", &engine));
        let (bids, _) = depth(&engine);
        assert_eq!(bids, vec![(50_000, 2)]); // 거절된 주문은 오더북에 흔적이 없다
    }

    #[test]
    fn trade_event_format_matches_team_contract() {
        let t = Trade {
            maker_order_id: 10,
            taker_order_id: 20,
            maker_user_id: 0,
            taker_user_id: 0,
            taker_side: Side::Buy,
            price: 50_000,
            qty: 3,
        };
        let v: Value = serde_json::from_str(&trade_event_body(7, &t, 1_724_000_000_000)).unwrap();
        assert_eq!(v["trade_id"], 7);
        assert_eq!(v["symbol"], "BTCKRW");
        assert_eq!(v["buy_order_id"], 20); // taker가 BUY면 taker가 매수자
        assert_eq!(v["sell_order_id"], 10);
        assert_eq!(v["price"], 50_000);
        assert_eq!(v["qty"], 3);
        assert_eq!(v["taker_side"], "BUY");
        assert_eq!(v["ts_ms"], 1_724_000_000_000u64);

        // taker가 SELL이면 maker가 매수자
        let t = Trade { taker_side: Side::Sell, ..t };
        let v: Value = serde_json::from_str(&trade_event_body(8, &t, 1)).unwrap();
        assert_eq!(v["buy_order_id"], 10);
        assert_eq!(v["sell_order_id"], 20);
    }

    /// 부분 체결: 큰 주문 하나가 반대편 여러 주문과 만나면 체결 이벤트가 체결 건수만큼 나온다.
    #[test]
    fn partial_fills_emit_one_event_each() {
        let (engine, events) = start_engine();
        assert!(accept_order(br#"{"order_id":1,"symbol":"BTCKRW","side":"SELL","price":50000,"qty":1}"#, &engine));
        assert!(accept_order(br#"{"order_id":2,"symbol":"BTCKRW","side":"SELL","price":50000,"qty":1}"#, &engine));
        assert!(accept_order(br#"{"order_id":3,"symbol":"BTCKRW","side":"BUY","price":50000,"qty":3}"#, &engine));

        // 주문 3은 1·2와 부분 체결 2건, 남은 1개는 매수 대기.
        let e1 = events.recv().unwrap();
        let e2 = events.recv().unwrap();
        assert_eq!((e1.maker_order_id, e1.qty), (1, 1));
        assert_eq!((e2.maker_order_id, e2.qty), (2, 1));
        let (bids, asks) = depth(&engine);
        assert_eq!(bids, vec![(50_000, 1)]);
        assert!(asks.is_empty());
    }
}
