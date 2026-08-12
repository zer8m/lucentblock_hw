//! 블로그의 4가지 상황을 그대로 돌려보는 데모. `cargo run`

use lucent::{Order, OrderBook, Side};

fn main() {
    let mut book = OrderBook::new();

    let steps = [
        ("A: 10,000원에 1개 매수", Order::limit(1, 1, Side::Buy, 10_000, 1, 1)),
        ("B: 9,000원에 1개 매수", Order::limit(2, 2, Side::Buy, 9_000, 1, 2)),
        ("C: 12,000원에 2개 매도", Order::limit(3, 3, Side::Sell, 12_000, 2, 3)),
        ("D: 10,000원에 1개 매도", Order::limit(4, 4, Side::Sell, 10_000, 1, 4)),
    ];

    for (label, order) in steps {
        println!("\n== {label} ==");
        for t in book.process_order(order) {
            println!(
                "체결! 가격 {} / 수량 {} (maker #{} <- taker #{})",
                t.price, t.qty, t.maker_order_id, t.taker_order_id
            );
        }
        print_book(&book);
    }
}

fn print_book(book: &OrderBook) {
    let fmt = |rows: Vec<(u64, u64)>| {
        if rows.is_empty() {
            "(비어있음)".to_string()
        } else {
            rows.iter().map(|(p, q)| format!("{p}원x{q}")).collect::<Vec<_>>().join(", ")
        }
    };
    println!("  매도: {}", fmt(book.depth(Side::Sell)));
    println!("  매수: {}", fmt(book.depth(Side::Buy)));
}
