//! 가격-시간 우선 원칙(price-time priority) 매칭 엔진.
//!
//! 오더북 = BTreeMap<가격, VecDeque<주문>>
//!   - BTreeMap: 가격이 항상 정렬 -> 최우선 호가를 O(log N)에 찾음
//!   - VecDeque: 같은 가격 안에서는 먼저 들어온 주문이 앞(선착순)

use std::collections::{BTreeMap, VecDeque};

pub type OrderId = u64;
/// 가격. 부동소수점 오차를 피하려고 항상 정수(최소 호가 단위 기준)로 다룬다.
pub type Price = u64;
/// 수량. 가격과 같은 이유로 정수.
pub type Qty = u64;

/// 주문 방향
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Side {
    Buy,  // 매수
    Sell, // 매도
}

/// 주문 종류
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OrderType {
    Limit,  // 지정가: 못 채우면 오더북에 남아 대기
    Market, // 시장가: 즉시 체결 가능한 만큼만 체결, 나머지는 취소
}

#[derive(Debug, Clone)]
pub struct Order {
    pub id: OrderId,
    pub user_id: u64,
    pub side: Side,
    pub order_type: OrderType,
    /// 시장가 주문에서는 무시된다.
    pub price: Price,
    pub qty: Qty,
    pub remaining_qty: Qty,
    pub timestamp: u64,
}

impl Order {
    pub fn limit(id: OrderId, user_id: u64, side: Side, price: Price, qty: Qty, timestamp: u64) -> Self {
        Self { id, user_id, side, order_type: OrderType::Limit, price, qty, remaining_qty: qty, timestamp }
    }

    pub fn market(id: OrderId, user_id: u64, side: Side, qty: Qty, timestamp: u64) -> Self {
        Self { id, user_id, side, order_type: OrderType::Market, price: 0, qty, remaining_qty: qty, timestamp }
    }
}

/// 체결 영수증. 가격은 항상 오더북에서 기다리던 maker의 가격이다.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Trade {
    pub maker_order_id: OrderId,
    pub taker_order_id: OrderId,
    pub price: Price,
    pub qty: Qty,
}

#[derive(Debug, Default)]
pub struct OrderBook {
    bids: BTreeMap<Price, VecDeque<Order>>,
    asks: BTreeMap<Price, VecDeque<Order>>,
}

impl OrderBook {
    pub fn new() -> Self {
        Self::default()
    }

    /// 매수 최우선 호가(가장 비싼 값).
    pub fn best_bid(&self) -> Option<Price> {
        self.bids.keys().next_back().copied()
    }

    /// 매도 최우선 호가(가장 싼 값).
    pub fn best_ask(&self) -> Option<Price> {
        self.asks.keys().next().copied()
    }

    /// 호가창 스냅샷. 매수는 비싼 순, 매도는 싼 순으로 (가격, 잔량).
    pub fn depth(&self, side: Side) -> Vec<(Price, Qty)> {
        let sum = |q: &VecDeque<Order>| -> Qty { q.iter().map(|o| o.remaining_qty).sum() };
        match side {
            Side::Buy => self.bids.iter().rev().map(|(&p, q)| (p, sum(q))).collect(),
            Side::Sell => self.asks.iter().map(|(&p, q)| (p, sum(q))).collect(),
        }
    }

    /// 주문 하나를 처리하고 발생한 체결 목록을 돌려준다.
    ///
    /// 지정가: 반대편에서 조건이 맞는 만큼 체결하고, 남으면 오더북에 등록되어 maker가 된다.
    /// 시장가: 체결 가능한 만큼만 체결하고 남은 수량은 취소(오더북에 등록하지 않음).
    pub fn process_order(&mut self, mut taker: Order) -> Vec<Trade> {
        let mut trades = Vec::new();

        while taker.remaining_qty > 0 {
            // 1단계: 반대편 최우선 호가를 본다.
            let book = if taker.side == Side::Buy { &mut self.asks } else { &mut self.bids };
            let best = match taker.side {
                Side::Buy => book.keys().next().copied(),
                Side::Sell => book.keys().next_back().copied(),
            };
            let Some(price) = best else { break };
            if !crosses(&taker, price) {
                break;
            }

            // 2단계: 그 가격의 대기줄 앞에서부터 체결한다.
            let queue = book.get_mut(&price).expect("best price must exist");
            while taker.remaining_qty > 0 {
                let Some(maker) = queue.front_mut() else { break };
                let qty = taker.remaining_qty.min(maker.remaining_qty);
                taker.remaining_qty -= qty;
                maker.remaining_qty -= qty;
                trades.push(Trade {
                    maker_order_id: maker.id,
                    taker_order_id: taker.id,
                    price, // maker 가격으로 체결
                    qty,
                });
                // 3단계: 다 체결된 maker는 줄에서 뺀다.
                if maker.remaining_qty == 0 {
                    queue.pop_front();
                }
            }
            if queue.is_empty() {
                book.remove(&price);
            }
        }

        // 지정가에서 남은 물량은 스스로 maker가 되어 대기.
        if taker.order_type == OrderType::Limit && taker.remaining_qty > 0 {
            let book = if taker.side == Side::Buy { &mut self.bids } else { &mut self.asks };
            book.entry(taker.price).or_default().push_back(taker);
        }

        trades
    }

}

/// taker가 이 가격의 maker와 거래할 수 있는가?
fn crosses(taker: &Order, maker_price: Price) -> bool {
    match (taker.order_type, taker.side) {
        (OrderType::Market, _) => true, // 시장가는 가격을 안 따진다
        (OrderType::Limit, Side::Buy) => taker.price >= maker_price,
        (OrderType::Limit, Side::Sell) => taker.price <= maker_price,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 블로그 시나리오: A(10000 매수) -> B(9000 매수) -> C(12000 매도) -> D(10000 매도, 체결)
    #[test]
    fn blog_scenario() {
        let mut book = OrderBook::new();
        assert!(book.process_order(Order::limit(1, 1, Side::Buy, 10_000, 1, 1)).is_empty());
        assert!(book.process_order(Order::limit(2, 2, Side::Buy, 9_000, 1, 2)).is_empty());
        assert!(book.process_order(Order::limit(3, 3, Side::Sell, 12_000, 2, 3)).is_empty());
        assert_eq!(book.best_bid(), Some(10_000));
        assert_eq!(book.best_ask(), Some(12_000));

        let trades = book.process_order(Order::limit(4, 4, Side::Sell, 10_000, 1, 4));
        assert_eq!(
            trades,
            vec![Trade { maker_order_id: 1, taker_order_id: 4, price: 10_000, qty: 1 }]
        );
        // A는 빠지고 B가 1등, 매도는 C만 남는다.
        assert_eq!(book.depth(Side::Buy), vec![(9_000, 1)]);
        assert_eq!(book.depth(Side::Sell), vec![(12_000, 2)]);
    }

    #[test]
    fn price_priority_then_time_priority() {
        let mut book = OrderBook::new();
        book.process_order(Order::limit(1, 1, Side::Sell, 1_010, 5, 1)); // 비쌈
        book.process_order(Order::limit(2, 2, Side::Sell, 1_000, 5, 2)); // 쌈 = 우선
        book.process_order(Order::limit(3, 3, Side::Sell, 1_000, 5, 3)); // 같은 가격, 나중에 옴

        let trades = book.process_order(Order::limit(4, 4, Side::Buy, 1_010, 12, 4));
        let ids: Vec<_> = trades.iter().map(|t| (t.maker_order_id, t.price, t.qty)).collect();
        assert_eq!(ids, vec![(2, 1_000, 5), (3, 1_000, 5), (1, 1_010, 2)]);
        // taker가 1010까지 냈어도 체결가는 maker 가격이고, 남은 3개는 매수로 대기.
        assert_eq!(book.depth(Side::Sell), vec![(1_010, 3)]);
        assert!(book.depth(Side::Buy).is_empty());
    }

    #[test]
    fn market_order_does_not_rest() {
        let mut book = OrderBook::new();
        book.process_order(Order::limit(1, 1, Side::Sell, 1_000, 2, 1));

        let trades = book.process_order(Order::market(2, 2, Side::Buy, 5, 2));
        assert_eq!(trades, vec![Trade { maker_order_id: 1, taker_order_id: 2, price: 1_000, qty: 2 }]);
        // 못 채운 3개는 오더북에 남지 않는다.
        assert!(book.depth(Side::Buy).is_empty());
        assert!(book.depth(Side::Sell).is_empty());
    }

    #[test]
    fn no_cross_when_price_gap() {
        let mut book = OrderBook::new();
        book.process_order(Order::limit(1, 1, Side::Sell, 1_100, 1, 1));
        assert!(book.process_order(Order::limit(2, 2, Side::Buy, 1_000, 1, 2)).is_empty());
        assert_eq!(book.best_bid(), Some(1_000));
        assert_eq!(book.best_ask(), Some(1_100));
    }
}
