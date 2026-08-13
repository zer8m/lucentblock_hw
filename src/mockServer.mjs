//1초마다 가짜 체결 이벤트를 웹소켓으로 브로드캐스트합니다.
//프론트에서 주문 요청이 오면 자동으로 체결 이벤트로 변환해서 다시 쏘아줍니다 (왕복1회) 

// mockServer.mjs
import { WebSocketServer } from 'ws';

const wss = new WebSocketServer({ port: 8080 });
console.log('🚀 가짜 WebSocket 서버가 ws://localhost:8080 에서 실행 중입니다.');

let tradeId = 1;

// 접속한 모든 클라이언트에게 데이터 전송
function broadcast(data) {
  wss.clients.forEach((client) => {
    if (client.readyState === 1) { // OPEN
      client.send(JSON.stringify(data));
    }
  });
}

// 1초마다 자동 가짜 체결 이벤트 생성 (엔진 기다리지 않고 진행)
setInterval(() => {
  const fakeTrade = {
    trade_id: tradeId++,
    symbol: "BTCKRW",
    buy_order_id: Math.floor(Math.random() * 100),
    sell_order_id: Math.floor(Math.random() * 100),
    price: 95000000 + Math.floor(Math.random() * 500000),
    qty: Number((Math.random() * 0.5).toFixed(2)),
    taker_side: Math.random() > 0.5 ? "BUY" : "SELL",
    ts_ms: Date.now()
  };
  broadcast({ type: 'TRADE', data: fakeTrade });
}, 1500);

wss.on('connection', (ws) => {
  console.log('⚡ 프론트엔드 연결됨!');

  // 프론트엔드에서 주문(왕복 테스트) 메시지가 도착했을 때
  ws.on('message', (message) => {
    try {
      const parsed = JSON.parse(message);
      if (parsed.action === 'CREATE_ORDER') {
        const { side, price, qty } = parsed.data;
        
        // 주문 접수 즉시 체결 이벤트로 응답 ("왕복 1회" 검증용)
        const orderTrade = {
          trade_id: tradeId++,
          symbol: "BTCKRW",
          buy_order_id: side === 'BUY' ? 999 : 100,
          sell_order_id: side === 'SELL' ? 999 : 100,
          price: Number(price),
          qty: Number(qty),
          taker_side: side,
          ts_ms: Date.now()
        };

        // 전체 화면에 갱신 알림
        broadcast({ type: 'TRADE', data: orderTrade });
      }
    } catch (e) {
      console.error('메시지 파싱 에러:', e);
    }
  });
});