// src/App.tsx
import { useState, useEffect } from 'react';

interface Trade {
  trade_id: number;
  symbol: string;
  price: number;
  qty: number;
  taker_side: 'BUY' | 'SELL';
  ts_ms: number;
}

export default function App() {
  const [ws, setWs] = useState<WebSocket | null>(null);
  const [trades, setTrades] = useState<Trade[]>([]);
  const [price, setPrice] = useState<number>(95200000);
  const [quantity, setQuantity] = useState<number>(0.1);
  const [isConnected, setIsConnected] = useState<boolean>(false);

  // 1. WebSocket 연결 설정
  useEffect(() => {
    const socket = new WebSocket('ws://localhost:8080');

    socket.onopen = () => {
      console.log('✅ WebSocket 서버 연결 성공!');
      setIsConnected(true);
    };

    socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.type === 'TRADE') {
        const newTrade: Trade = message.data;
        // 새로운 체결 내역을 상단에 추가 (최대 10개 유지)
        setTrades((prev) => [newTrade, ...prev.slice(0, 9)]);
      }
    };

    socket.onclose = () => {
      console.log('❌ WebSocket 연결 종료');
      setIsConnected(false);
    };

    setWs(socket);

    return () => {
      socket.close();
    };
  }, []);

  // 2. 주문 창 버튼 클릭 시 웹소켓 전송 ("왕복 1회" 테스트)
  const handleOrder = (side: 'BUY' | 'SELL') => {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      alert('웹소켓 서버가 연결되어 있지 않습니다!');
      return;
    }

    const orderPayload = {
      action: 'CREATE_ORDER',
      data: {
        account_id: 1,
        symbol: 'BTCKRW',
        side: side,
        price: price,
        qty: quantity
      }
    };

    ws.send(JSON.stringify(orderPayload));
    console.log('🚀 [서버로 주문 전송]', orderPayload);
  };

  return (
    <div style={{ padding: '20px', background: '#1e1e1e', color: '#fff', minHeight: '100vh', fontFamily: 'sans-serif' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2>⚡ 거래소 실시간 화면 (BTC/KRW)</h2>
        <span style={{ fontSize: '12px', padding: '4px 8px', borderRadius: '4px', background: isConnected ? '#2e8b62' : '#bf4545' }}>
          {isConnected ? '🟢 WebSocket 연결됨' : '🔴 연결 안됨 (mockServer 확인)'}
        </span>
      </header>

      {/* 3분할 레이아웃 Grid[cite: 1] */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '20px', marginTop: '20px' }}>
        
        {/* 1. 오더북 (가짜 이벤트로 상단가 실시간 표시)[cite: 1] */}
        <div style={{ background: '#2a2a2a', padding: '15px', borderRadius: '8px' }}>
          <h3>오더북</h3>
          <div style={{ color: '#ff4d4f', marginBottom: '10px' }}>
            <small>매도 호가 (SELL)</small>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
              <span>95,500,000</span>
              <span>0.12 BTC</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
              <span>95,400,000</span>
              <span>0.45 BTC</span>
            </div>
          </div>

          <div style={{ borderTop: '1px solid #444', borderBottom: '1px solid #444', padding: '8px 0', textAlign: 'center', fontWeight: 'bold' }}>
            최근 체결가: {trades.length > 0 ? trades[0].price.toLocaleString() : '95,200,000'} KRW
          </div>

          <div style={{ color: '#52c41a', marginTop: '10px' }}>
            <small>매수 호가 (BUY)</small>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
              <span>95,100,000</span>
              <span>0.85 BTC</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
              <span>95,000,000</span>
              <span>2.10 BTC</span>
            </div>
          </div>
        </div>

        {/* 2. 주문 창[cite: 1] */}
        <div style={{ background: '#2a2a2a', padding: '15px', borderRadius: '8px' }}>
          <h3>주문하기</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '15px', marginTop: '20px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '12px', marginBottom: '5px' }}>가격 (KRW)</label>
              <input
                type="number"
                value={price}
                onChange={(e) => setPrice(Number(e.target.value))}
                style={{ width: '100%', padding: '8px', background: '#111', color: '#fff', border: '1px solid #555' }}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '12px', marginBottom: '5px' }}>수량 (BTC)</label>
              <input
                type="number"
                step="0.01"
                value={quantity}
                onChange={(e) => setQuantity(Number(e.target.value))}
                style={{ width: '100%', padding: '8px', background: '#111', color: '#fff', border: '1px solid #555' }}
              />
            </div>
            <div style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
              <button
                onClick={() => handleOrder('BUY')}
                style={{ flex: 1, padding: '10px', background: '#52c41a', color: '#fff', border: 'none', fontWeight: 'bold', cursor: 'pointer' }}
              >
                매수 (BUY)
              </button>
              <button
                onClick={() => handleOrder('SELL')}
                style={{ flex: 1, padding: '10px', background: '#ff4d4f', color: '#fff', border: 'none', fontWeight: 'bold', cursor: 'pointer' }}
              >
                매도 (SELL)
              </button>
            </div>
          </div>
        </div>

        {/* 3. 실시간 체결 내역 (웹소켓 수신 자동 갱신)[cite: 1] */}
        <div style={{ background: '#2a2a2a', padding: '15px', borderRadius: '8px' }}>
          <h3>실시간 체결 내역</h3>
          <div style={{ marginTop: '10px' }}>
            {trades.map((t) => (
              <div key={t.trade_id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #333', fontSize: '13px' }}>
                <span style={{ color: t.taker_side === 'BUY' ? '#52c41a' : '#ff4d4f', fontWeight: 'bold' }}>
                  {t.price.toLocaleString()} KRW
                </span>
                <span>{t.qty} BTC</span>
                <span style={{ color: '#888', fontSize: '11px' }}>
                  {new Date(t.ts_ms).toLocaleTimeString()}
                </span>
              </div>
            ))}
          </div>
        </div>

      </div>
    </div>
  );
}