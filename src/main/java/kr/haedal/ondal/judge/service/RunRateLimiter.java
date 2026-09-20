package kr.haedal.ondal.judge.service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "내 입력으로 실행" 남용 방지 - 사용자당 1분 슬라이딩 윈도 안에서 10회 (docs hoj/api.md 8절).
 * 메모리 카운터: 서버가 하나이고(재시작하면 초기화되어도 무방) 한도가 작아 DB 를 쓸 이유가 없다. 재검토 조건(결정 13): Judge0 부하가 보이면 한도 하향.
 */
@Component
public class RunRateLimiter {

    public static final int LIMIT_PER_MINUTE = 10;
    private static final long WINDOW_MS = 60_000L;

    /** 사용자 id → 최근 호출 시각(ms) 큐. 윈도 밖 시각은 다음 호출 때 걷어낸다 */
    private final Map<Long, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    /** 한도 안이면 이번 호출을 기록하고 true, 한도면 false (기록하지 않는다) */
    public boolean tryAcquire(Long userId) {
        return tryAcquire(userId, System.currentTimeMillis());
    }

    boolean tryAcquire(Long userId, long nowMs) {
        ArrayDeque<Long> window = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && nowMs - window.peekFirst() >= WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= LIMIT_PER_MINUTE) {
                return false;
            }
            window.addLast(nowMs);
            return true;
        }
    }

    /** 테스트 격리용 - 테스트마다 사용자 id 가 1부터 다시 시작하므로 이전 테스트의 기록을 비운다 */
    public void clear() {
        hits.clear();
    }
}
