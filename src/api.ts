// src/api.ts
const BASE_URL = 'http://localhost:8080';

export interface OrderRequest {
  account_id?: number;
  symbol: string;
  side: 'BUY' | 'SELL';
  price: number;
  qty: number;
}

export interface BalanceItem {
  asset: string;
  available: number;
  locked: number;
}

export interface OrderItem {
  order_id: number;
  symbol: string;
  side: 'BUY' | 'SELL';
  price: number;
  qty: number;
  filled_qty: number;
  status: string;
  created_at: number;
}

export interface TradeItem {
  trade_id: number;
  symbol?: string;
  buy_order_id?: number;
  sell_order_id?: number;
  price: number;
  qty: number;
  taker_side: 'BUY' | 'SELL';
  ts_ms: number;
}

// 1. 주문 접수
export async function placeOrder(order: OrderRequest) {
  const payload = {
    account_id: order.account_id ?? 1,
    symbol: order.symbol || 'BTCKRW',
    side: order.side,
    price: Number(order.price),
    qty: Number(order.qty),
  };

  const res = await fetch(`${BASE_URL}/api/v1/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    let errorCode = 'UNKNOWN_ERROR';
    try {
      const errData = await res.json();
      errorCode = errData.code || errData.message || `HTTP_${res.status}`;
    } catch {
      errorCode = `HTTP_${res.status}`;
    }
    throw new Error(errorCode);
  }

  return res.json();
}

// 2. 잔고 조회
export async function fetchBalances(accountId: number = 1): Promise<BalanceItem[]> {
  const res = await fetch(`${BASE_URL}/api/v1/balances?account_id=${accountId}`);
  if (!res.ok) throw new Error('잔고 조회 실패');
  const data = await res.json();
  return data.balances || [];
}

// 3. 주문 목록 조회
export async function fetchOrders(accountId: number = 1, status: string = 'OPEN'): Promise<OrderItem[]> {
  const res = await fetch(`${BASE_URL}/api/v1/orders?account_id=${accountId}&status=${status}&limit=50`);
  if (!res.ok) throw new Error('주문 목록 조회 실패');
  const data = await res.json();
  return data.orders || [];
}

// 4. 최근 체결 내역 조회
export async function fetchTrades(symbol: string = 'BTCKRW'): Promise<TradeItem[]> {
  const res = await fetch(`${BASE_URL}/api/v1/trades?symbol=${symbol}&limit=50`);
  if (!res.ok) throw new Error('체결 내역 조회 실패');
  const data = await res.json();
  return data.trades || [];
}