use std::collections::{BTreeMap, VecDeque};
use std::io::{self, Write};
use std::time::{SystemTime, UNIX_EPOCH};

// 1. Order 구조체 정의 (id, 매수/매도, 가격, 수량, 시간)
#[derive(Debug, Clone, PartialEq)]
pub enum Side {
    Buy,
    Sell,
}

#[derive(Debug, Clone)]
pub struct Order {
    pub id: u64,
    pub side: Side,
    pub price: u64,
    pub qty: u64,
    pub ts: u64, // 체크리스트 요구사항: 시간 필드 추가
}

// 체결 이벤트 구조체 (확정된 형식)
#[derive(Debug)]
pub struct TradeEvent {
    pub trade_id: u64,
    pub buy_order_id: u64,
    pub sell_order_id: u64,
    pub price: u64,
    pub qty: u64,
    pub ts: u64,
}

// 2. 오더북 뼈대: BTreeMap<가격, VecDeque<Order>>
pub struct OrderBook {
    pub bids: BTreeMap<u64, VecDeque<Order>>, // 매수 (비싼 가격 우선)
    pub asks: BTreeMap<u64, VecDeque<Order>>, // 매도 (싼 가격 우선)
    pub next_trade_id: u64,
    pub next_order_id: u64, // 주문 ID 자동 발급용
}

impl OrderBook {
    pub fn new() -> Self {
        OrderBook {
            bids: BTreeMap::new(),
            asks: BTreeMap::new(),
            next_trade_id: 1,
            next_order_id: 1,
        }
    }

    // 3. 오더북 매칭 로직 완성 (가격-시간 우선순위)
    pub fn add_order(&mut self, mut order: Order) {
        let mut trades = Vec::new();

        if order.side == Side::Buy {
            // 매수: 가장 싼 매도(asks의 가장 작은 키)부터 확인
            while order.qty > 0 {
                let best_ask_price = match self.asks.keys().next().copied() {
                    Some(price) => price,
                    None => break,
                };

                if best_ask_price > order.price {
                    break;
                }

                if let Some(queue) = self.asks.get_mut(&best_ask_price) {
                    while order.qty > 0 && !queue.is_empty() {
                        let resting_order = queue.front_mut().unwrap();
                        // 4. 부분 체결 처리
                        let match_qty = std::cmp::min(order.qty, resting_order.qty);

                        // 5. 체결 이벤트 발행
                        trades.push(TradeEvent {
                            trade_id: self.next_trade_id,
                            buy_order_id: order.id,
                            sell_order_id: resting_order.id,
                            price: best_ask_price,
                            qty: match_qty,
                            ts: get_current_timestamp(),
                        });
                        self.next_trade_id += 1;

                        order.qty -= match_qty;
                        resting_order.qty -= match_qty;

                        if resting_order.qty == 0 {
                            queue.pop_front();
                        }
                    }
                }

                if self.asks.get(&best_ask_price).unwrap().is_empty() {
                    self.asks.remove(&best_ask_price);
                }
            }

            if order.qty > 0 {
                self.bids.entry(order.price).or_insert_with(VecDeque::new).push_back(order);
            }
        } else {
            // 매도: 가장 비싼 매수(bids의 가장 큰 키)부터 확인
            while order.qty > 0 {
                let best_bid_price = match self.bids.keys().next_back().copied() {
                    Some(price) => price,
                    None => break,
                };

                if best_bid_price < order.price {
                    break;
                }

                if let Some(queue) = self.bids.get_mut(&best_bid_price) {
                    while order.qty > 0 && !queue.is_empty() {
                        let resting_order = queue.front_mut().unwrap();
                        let match_qty = std::cmp::min(order.qty, resting_order.qty);

                        trades.push(TradeEvent {
                            trade_id: self.next_trade_id,
                            buy_order_id: resting_order.id,
                            sell_order_id: order.id,
                            price: best_bid_price,
                            qty: match_qty,
                            ts: get_current_timestamp(),
                        });
                        self.next_trade_id += 1;

                        order.qty -= match_qty;
                        resting_order.qty -= match_qty;

                        if resting_order.qty == 0 {
                            queue.pop_front();
                        }
                    }
                }

                if self.bids.get(&best_bid_price).unwrap().is_empty() {
                    self.bids.remove(&best_bid_price);
                }
            }

            if order.qty > 0 {
                self.asks.entry(order.price).or_insert_with(VecDeque::new).push_back(order);
            }
        }

        for trade in trades {
            println!("[체결 이벤트 발행] {:?}", trade);
        }
    }

    // 오더북 상태 출력 함수 (가격순 정렬 확인용)
    pub fn print_book(&self) {
        println!("\n=== 현재 오더북 상태 ===");
        println!("[매도(Asks) - 낮은 가격 우선]");
        // 매도는 거꾸로 출력해야 싼 가격이 아래쪽에 예쁘게 보입니다.
        for (price, queue) in self.asks.iter().rev() {
            let total_qty: u64 = queue.iter().map(|o| o.qty).sum();
            println!("가격: {}, 대기 수량: {} (주문 {}건)", price, total_qty, queue.len());
        }
        println!("------------------------");
        println!("[매수(Bids) - 높은 가격 우선]");
        // 매수는 비싼 가격부터 내림차순 출력
        for (price, queue) in self.bids.iter().rev() {
            let total_qty: u64 = queue.iter().map(|o| o.qty).sum();
            println!("가격: {}, 대기 수량: {} (주문 {}건)", price, total_qty, queue.len());
        }
        println!("========================\n");
    }
}

fn get_current_timestamp() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs()
}

// 6. 터미널에서 매수/매도 주문 넣어서 체결 로그 찍히는지 확인
fn main() {
    let mut orderbook = OrderBook::new();
    println!("매칭 엔진 테스트를 시작합니다.");
    println!("입력 방법: <buy/sell> <가격> <수량> (예: buy 100 10)");
    println!("종료하려면 'exit'를 입력하세요.\n");

    loop {
        print!("> ");
        io::stdout().flush().unwrap(); // 터미널 프롬프트 출력

        let mut input = String::new();
        io::stdin().read_line(&mut input).unwrap();
        let input = input.trim();

        if input == "exit" {
            break;
        }

        let parts: Vec<&str> = input.split_whitespace().collect();
        if parts.len() != 3 {
            println!("잘못된 입력입니다. 'buy 100 10' 형식으로 입력해주세요.");
            continue;
        }

        let side = match parts[0] {
            "buy" => Side::Buy,
            "sell" => Side::Sell,
            _ => {
                println!("방향은 'buy' 또는 'sell' 이어야 합니다.");
                continue;
            }
        };

        let price: u64 = match parts[1].parse() {
            Ok(p) => p,
            Err(_) => { println!("가격은 숫자여야 합니다."); continue; }
        };

        let qty: u64 = match parts[2].parse() {
            Ok(q) => q,
            Err(_) => { println!("수량은 숫자여야 합니다."); continue; }
        };

        let order = Order {
            id: orderbook.next_order_id,
            side,
            price,
            qty,
            ts: get_current_timestamp(),
        };
        orderbook.next_order_id += 1;

        println!("\n주문 접수: {:?}", order);
        orderbook.add_order(order);
        orderbook.print_book(); // 주문 처리 후 항상 오더북 상태 출력
    }
}