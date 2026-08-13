// src/mockData.ts

// 1. 오더북 (호가창) 데이터
export const mockOrderBook = {
  asks: [ // 매도 호가 (위쪽 - Red)
    { price: 95500000, quantity: 0.12 },
    { price: 95400000, quantity: 0.45 },
    { price: 95300000, quantity: 1.20 },
  ],
  bids: [ // 매수 호가 (아래쪽 - Green)
    { price: 95200000, quantity: 0.85 },
    { price: 95100000, quantity: 2.10 },
    { price: 95000000, quantity: 5.00 },
  ]
};

// 2. 체결 내역 데이터
export const mockTrades = [
  { id: '1', price: 95250000, quantity: 0.05, type: 'buy', time: '16:30:02' },
  { id: '2', price: 95200000, quantity: 0.12, type: 'sell', time: '16:29:58' },
];

// 3. 내 잔고 데이터
export const mockBalance = {
  krw: 10000000, // 1천만원
  btc: 0.5       // 0.5 BTC
};