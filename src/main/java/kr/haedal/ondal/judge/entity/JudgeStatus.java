package kr.haedal.ondal.judge.entity;

/** 채점 진행 상태 - PENDING(큐 대기, 엔진 미연결이면 여기 머문다) → RUNNING(엔진 실행 중) → DONE(판정 있음) / ERROR(엔진 장애 - verdict JUDGE_ERROR) */
public enum JudgeStatus {
    PENDING, RUNNING, DONE, ERROR
}
