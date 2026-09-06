import { useState, useEffect, useRef } from 'react';
import {
  placeOrder,
  fetchBalances,
  fetchOrders,
  fetchTrades,
} from './api';
import type { BalanceItem, OrderItem, TradeItem } from './api';
import { formatTime, formatNumber } from './utils';

export default function App() {
  const [balances, setBalances] = useState<BalanceItem[]>([]);
  const [orders, setOrders] = useState<OrderItem[]>([]);
  const [trades, setTrades] = useState<TradeItem[]>([]);
  const [price, setPrice] = useState<string>('1000');
  const [qty, setQty] = useState<string>('1');
  const [wsConnected, setWsConnected] = useState<boolean>(false);
  const [lastTakerSide, setLastTakerSide] = useState<'BUY' | 'SELL'>('BUY');
  const [currentPrice, setCurrentPrice] = useState<number>(1000);

  const wsRef = useRef<WebSocket | null>(null);

  // 1. 초기 데이터 조회 함수
  const loadInitialData = async () => {
    try {
      const [balData, ordData, trdData] = await Promise.all([
        fetchBalances(1),
        fetchOrders(1, 'OPEN'),
        fetchTrades('BTCKRW'),
      ]);
      setBalances(balData);
      setOrders(ordData);
      setTrades(trdData);
      if (trdData.length > 0) {
        setCurrentPrice(trdData[0].price);
        setLastTakerSide(trdData[0].taker_side);
      }
    } catch (err) {
      console.warn('초기 API 데이터 로드 대기 중 (백엔드 서버 확인 필요):', err);
    }
  };

  // 2. 컴포넌트 마운트 시 초기 조회 & 웹소켓 연결 (자동 재연결 포함)
useEffect(() => {
  loadInitialData();

  let reconnectTimer: NodeJS.Timeout;

  const connectWebSocket = () => {
    const ws = new WebSocket('ws://localhost:8080/ws');
    wsRef.current = ws;

    ws.onopen = () => {
      console.log('✅ WebSocket 서버 연결 완료');
      setWsConnected(true);
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        const tradeData: TradeItem = msg.data || msg;

        if (tradeData.price && tradeData.qty) {
          setTrades((prev) => [tradeData, ...prev.slice(0, 49)]);
          setCurrentPrice(tradeData.price);

          if (tradeData.taker_side) {
            setLastTakerSide(tradeData.taker_side);
          }

          loadInitialData();
        }
      } catch (e) {
        console.error('웹소켓 메시지 파싱 에러:', e);
      }
    };

    ws.onclose = () => {
      console.log('❌ WebSocket 연결 종료. 3초 후 재연결 시도...');
      setWsConnected(false);

      // 연결이 끊기면 3초 뒤에 재연결 함수 실행
      reconnectTimer = setTimeout(() => {
        connectWebSocket();
      }, 3000);
    };

    ws.onerror = (error) => {
      console.error('WebSocket 에러 발생:', error);
      ws.close(); // 에러 발생 시 close를 유도하여 onclose에서 재연결 처리
    };
  };

  connectWebSocket();

  return () => {
    clearTimeout(reconnectTimer);
    if (wsRef.current) {
      wsRef.current.close();
    }
  };
}, []);

  // 3. 주문 제출 핸들러
  const handleOrderSubmit = async (side: 'BUY' | 'SELL') => {
    if (!price || Number(price) <= 0 || !qty || Number(qty) <= 0) {
      alert('⚠️ 가격과 수량을 올바르게 입력해주세요.');
      return;
    }

    try {
      await placeOrder({
        account_id: 1,
        symbol: 'BTCKRW',
        side,
        price: Number(price),
        qty: Number(qty),
      });
      alert(`✅ ${side === 'BUY' ? '매수' : '매도'} 주문이 접수되었습니다.`);
      loadInitialData();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : String(error);
      switch (message) {
        case 'INSUFFICIENT_BALANCE':
          alert('❌ [잔고 부족] 잔여 잔고가 부족하여 주문할 수 없습니다.');
          break;
        case 'INVALID_PARAM':
          alert('❌ [입력 오류] 가격 또는 수량이 올바르지 않습니다.');
          break;
        case 'ENGINE_UNAVAILABLE':
          alert('⚠️ [엔진 점검] 매칭 엔진 서버가 동작하지 않습니다. 백엔드 상태를 확인해주세요.');
          break;
        case 'ORDER_NOT_FOUND':
          alert('❌ 주문을 찾을 수 없습니다.');
          break;
        case 'ORDER_NOT_CANCELABLE':
          alert('❌ 이미 체결되었거나 취소할 수 없는 주문입니다.');
          break;
        default:
          alert(`⚠️ 주문 요청 실패 (오류: ${message})`);
      }
    }
  };

  // 색상 테마: BUY(매수) 빨강, SELL(매도) 파랑
  const buyColor = '#ef4444';
  const sellColor = '#3b82f6';

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', backgroundColor: '#0f172a', color: '#f8fafc', minHeight: '100vh' }}>
      {/* 상단 상태 바 */}
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #334155', paddingBottom: '12px', marginBottom: '20px' }}>
        <h2 style={{ margin: 0 }}>거래소 대시보드 (BTC/KRW)</h2>
        <span style={{ padding: '4px 10px', borderRadius: '4px', fontSize: '13px', backgroundColor: wsConnected ? '#166534' : '#991b1b' }}>
          {wsConnected ? '🟢 WebSocket 연결됨' : '🔴 WebSocket 연결 끊김'}
        </span>
      </header>

      {/* 보유 잔고 섹션 */}
      <section style={{ marginBottom: '20px', padding: '12px', backgroundColor: '#1e293b', borderRadius: '8px' }}>
        <strong>내 잔고 (Account #1):</strong>
        <div style={{ display: 'flex', gap: '20px', marginTop: '8px' }}>
          {balances.length === 0 ? (
            <span style={{ color: '#94a3b8' }}>잔고 조회 대기 중...</span>
          ) : (
            balances.map((b) => (
              <div key={b.asset}>
                {b.asset}: <strong>{formatNumber(b.available)}</strong> (주문중: {formatNumber(b.locked)})
              </div>
            ))
          )}
        </div>
      </section>

      {/* 3분할 메인 레이아웃 */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1.2fr', gap: '20px' }}>
        
        {/* 1. 호가창 (현재가 및 taker_side 색칠 반영) */}
        <div style={{ backgroundColor: '#1e293b', padding: '16px', borderRadius: '8px' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #334155', paddingBottom: '8px' }}>호가창</h3>
          
          {/* 최근 체결 방향 및 현재 체결가 강조 */}
          <div style={{
            padding: '10px',
            textAlign: 'center',
            backgroundColor: '#0f172a',
            borderRadius: '6px',
            marginBottom: '12px',
            border: `1px solid ${lastTakerSide === 'BUY' ? buyColor : sellColor}`
          }}>
            <div style={{ fontSize: '12px', color: '#94a3b8' }}>최근 체결 방향</div>
            <div style={{ fontSize: '18px', fontWeight: 'bold', color: lastTakerSide === 'BUY' ? buyColor : sellColor, marginTop: '2px' }}>
              {formatNumber(currentPrice)} KRW ({lastTakerSide === 'BUY' ? '매수 체결' : '매도 체결'})
            </div>
          </div>

          <table style={{ width: '100%', textAlign: 'right', fontSize: '14px' }}>
            <thead>
              <tr style={{ color: '#94a3b8' }}>
                <th style={{ textAlign: 'left' }}>구분</th>
                <th>가격(KRW)</th>
                <th>수량</th>
              </tr>
            </thead>
            <tbody>
              {/* 매도 호가 (파란색 계열) */}
              <tr style={{ color: sellColor }}>
                <td style={{ textAlign: 'left' }}>매도호가</td>
                <td>1,050</td>
                <td>2.50</td>
              </tr>
              <tr style={{ color: sellColor }}>
                <td style={{ textAlign: 'left' }}>매도호가</td>
                <td>1,000</td>
                <td>5.00</td>
              </tr>
              
              {/* 중앙 현재가 구분선 */}
              <tr style={{ backgroundColor: '#0f172a' }}>
                <td colSpan={3} style={{ textAlign: 'center', padding: '6px 0', fontSize: '12px', color: lastTakerSide === 'BUY' ? buyColor : sellColor, fontWeight: 'bold' }}>
                  ─── 현재가 {formatNumber(currentPrice)} ───
                </td>
              </tr>

              {/* 매수 호가 (빨간색 계열) */}
              <tr style={{ color: buyColor }}>
                <td style={{ textAlign: 'left' }}>매수호가</td>
                <td>950</td>
                <td>3.20</td>
              </tr>
              <tr style={{ color: buyColor }}>
                <td style={{ textAlign: 'left' }}>매수호가</td>
                <td>900</td>
                <td>1.80</td>
              </tr>
            </tbody>
          </table>
        </div>

        {/* 2. 주문 창 */}
        <div style={{ backgroundColor: '#1e293b', padding: '16px', borderRadius: '8px' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #334155', paddingBottom: '8px' }}>주문하기</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '16px' }}>
            <div>
              <label style={{ fontSize: '13px', color: '#94a3b8' }}>주문 가격 (KRW)</label>
              <input
                type="number"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                style={{ width: '100%', padding: '8px', marginTop: '4px', backgroundColor: '#0f172a', border: '1px solid #475569', color: '#fff', borderRadius: '4px' }}
              />
            </div>
            <div>
              <label style={{ fontSize: '13px', color: '#94a3b8' }}>주문 수량 (BTC)</label>
              <input
                type="number"
                value={qty}
                onChange={(e) => setQty(e.target.value)}
                style={{ width: '100%', padding: '8px', marginTop: '4px', backgroundColor: '#0f172a', border: '1px solid #475569', color: '#fff', borderRadius: '4px' }}
              />
            </div>
            <div style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
              <button
                onClick={() => handleOrderSubmit('BUY')}
                style={{ flex: 1, padding: '10px', backgroundColor: buyColor, color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}
              >
                매수 (BUY)
              </button>
              <button
                onClick={() => handleOrderSubmit('SELL')}
                style={{ flex: 1, padding: '10px', backgroundColor: sellColor, color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}
              >
                매도 (SELL)
              </button>
            </div>
          </div>
        </div>

        {/* 3. 체결 내역 & 미체결 주문 */}
        <div style={{ backgroundColor: '#1e293b', padding: '16px', borderRadius: '8px' }}>
          <h3 style={{ marginTop: 0, borderBottom: '1px solid #334155', paddingBottom: '8px' }}>실시간 체결 내역</h3>
          <div style={{ maxHeight: '200px', overflowY: 'auto', marginBottom: '20px' }}>
            <table style={{ width: '100%', textAlign: 'right', fontSize: '13px' }}>
              <thead>
                <tr style={{ color: '#94a3b8' }}>
                  <th style={{ textAlign: 'left' }}>시간</th>
                  <th>가격</th>
                  <th>수량</th>
                  <th>체결구분</th>
                </tr>
              </thead>
              <tbody>
                {trades.length === 0 ? (
                  <tr><td colSpan={4} style={{ textAlign: 'center', color: '#64748b', padding: '10px 0' }}>체결 데이터 없음</td></tr>
                ) : (
                  trades.map((t, idx) => (
                    <tr key={t.trade_id ? `${t.trade_id}-${idx}` : idx} style={{ color: t.taker_side === 'BUY' ? buyColor : sellColor }}>
                      <td style={{ textAlign: 'left', color: '#94a3b8' }}>{formatTime(t.ts_ms)}</td>
                      <td>{formatNumber(t.price)}</td>
                      <td>{t.qty}</td>
                      <td>
                        <span style={{
                          padding: '2px 6px',
                          borderRadius: '4px',
                          fontSize: '11px',
                          backgroundColor: t.taker_side === 'BUY' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(59, 130, 246, 0.2)',
                          color: t.taker_side === 'BUY' ? buyColor : sellColor
                        }}>
                          {t.taker_side === 'BUY' ? '매수' : '매도'}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <h3 style={{ borderBottom: '1px solid #334155', paddingBottom: '8px' }}>내 미체결 주문</h3>
          <div style={{ maxHeight: '150px', overflowY: 'auto' }}>
            <table style={{ width: '100%', textAlign: 'right', fontSize: '14px' }}>
            <thead>
              <tr style={{ color: '#94a3b8' }}>
                <th style={{ textAlign: 'left' }}>구분</th>
                <th>가격(KRW)</th>
                <th>수량</th>
              </tr>
            </thead>
            <tbody>
              {/* 매도 호가 (기본 파란색 계열, 최근 체결 방향이 SELL이면 강조) */}
              <tr style={{ 
                color: sellColor, 
                backgroundColor: lastTakerSide === 'SELL' ? 'rgba(59, 130, 246, 0.15)' : 'transparent',
                transition: 'background-color 0.2s'
              }}>
                <td style={{ textAlign: 'left' }}>매도호가</td>
                <td>1,050</td>
                <td>2.50</td>
              </tr>
              <tr style={{ 
                color: sellColor, 
                backgroundColor: lastTakerSide === 'SELL' ? 'rgba(59, 130, 246, 0.15)' : 'transparent',
                transition: 'background-color 0.2s'
              }}>
                <td style={{ textAlign: 'left' }}>매도호가</td>
                <td>1,000</td>
                <td>5.00</td>
              </tr>
              
              {/* 중앙 현재가 구분선 */}
              <tr style={{ backgroundColor: '#0f172a' }}>
                <td colSpan={3} style={{ textAlign: 'center', padding: '6px 0', fontSize: '12px', color: lastTakerSide === 'BUY' ? buyColor : sellColor, fontWeight: 'bold' }}>
                  ─── 현재가 {formatNumber(currentPrice)} ───
                </td>
              </tr>

              {/* 매수 호가 (기본 빨간색 계열, 최근 체결 방향이 BUY이면 강조) */}
              <tr style={{ 
                color: buyColor, 
                backgroundColor: lastTakerSide === 'BUY' ? 'rgba(239, 68, 68, 0.15)' : 'transparent',
                transition: 'background-color 0.2s'
              }}>
                <td style={{ textAlign: 'left' }}>매수호가</td>
                <td>950</td>
                <td>3.20</td>
              </tr>
              <tr style={{ 
                color: buyColor, 
                backgroundColor: lastTakerSide === 'BUY' ? 'rgba(239, 68, 68, 0.15)' : 'transparent',
                transition: 'background-color 0.2s'
              }}>
                <td style={{ textAlign: 'left' }}>매수호가</td>
                <td>900</td>
                <td>1.80</td>
              </tr>
            </tbody>
          </table>
          </div>
        </div>

      </div>
    </div>
  );
}
