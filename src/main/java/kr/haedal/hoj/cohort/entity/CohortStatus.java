package kr.haedal.hoj.cohort.entity;

/**
 * 분반 상태. 하드 삭제는 없다 — 학기가 끝나면 ARCHIVED(보관)로 보낸다.
 * 보관된 분반은 "얼어붙는다": 소속자 열람은 그대로, 변경(운영진 지정·과제 등록·제출 등)은 누구도 불가.
 * ADMIN이 restore 하면 다시 변경 가능. (docs: cohort/design.md §1)
 */
public enum CohortStatus {
    ACTIVE,
    ARCHIVED
}
