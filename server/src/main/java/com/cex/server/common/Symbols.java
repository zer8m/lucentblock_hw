package com.cex.server.common;

import com.cex.server.error.ApiException;
import com.cex.server.error.ErrorCode;

import java.util.Map;

/**
 * "BTCKRW" -> base=BTC(코인), quote=KRW(현금)
 *
 * 붙여쓰기라 어디서 잘라야 할지 알 수 없으므로 종목별로 미리 적어둔다.
 * 종목 추가할 때는 여기 한 줄만 넣으면 된다. 예: "ETHKRW" -> {"ETH", "KRW"}
 * 목록에 없는 종목은 바로 튕겨내므로 오타도 여기서 걸린다.
 */
public final class Symbols {

    private static final Map<String, String[]> MARKETS = Map.of(
            "BTCKRW", new String[]{"BTC", "KRW"}
    );

    private Symbols() {}

    public static String base(String symbol) {
        return lookup(symbol)[0];
    }

    public static String quote(String symbol) {
        return lookup(symbol)[1];
    }

    public static boolean isSupported(String symbol) {
        return symbol != null && MARKETS.containsKey(symbol);
    }

    private static String[] lookup(String symbol) {
        String[] pair = symbol == null ? null : MARKETS.get(symbol);
        if (pair == null) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "지원하지 않는 symbol입니다: " + symbol);
        }
        return pair;
    }
}
