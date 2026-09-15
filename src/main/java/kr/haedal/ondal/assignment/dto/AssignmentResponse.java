package kr.haedal.ondal.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.problem.dto.TagResponse;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.submission.entity.SubmissionStatus;

import java.time.Instant;
import java.util.List;

/**
 * 과제 응답 - 목록·단건·등록·수정 응답이 전부 이 하나의 모양이다.
 *
 * V7 이후 과제는 "문제를 분반에 배정한 것"이라, 제목·본문·번호는 배정된 문제(Problem)에서 온다.
 * 화면은 과제 한 줄을 그대로 그리면 되므로 중첩 대신 평평하게 펴서 내려준다 - 대신 문제 자체를 열려면 problemId 를 쓴다.
 * myStatus·submissionCount는 요청자에 따라 달라지므로 AssignmentResponseAssembler가 채운다.
 */
public record AssignmentResponse(
        Long id,

        @Schema(description = "배정된 문제 id - 문제 상세(HOJ)·채점 설정으로 갈 때 쓴다")
        Long problemId,

        @Schema(description = "문제 번호 - 전역 유일, 1000부터. 표시 형식(#1000)은 FE 몫")
        Integer problemNo,

        @Schema(description = "차시 번호 - 차시에 속하지 않는 과제는 null. 목록은 차시 오름차순(null 마지막) → 등록순")
        Integer sessionNo,

        @Schema(description = "문제 제목 - 배정된 문제의 것")
        String title,

        @Schema(description = "문제 본문 - 배정된 문제의 것 (선택)")
        String description,

        @Schema(description = "문제에 붙은 태그 - 이름순")
        List<TagResponse> tags,

        @Schema(description = "마감 시각(UTC) - KST 변환은 프론트 몫. 마감이 수정되면 지각 판정도 새 마감 기준으로 재계산된다")
        Instant dueAt,

        Instant createdAt,

        @Schema(description = "요청자 본인의 제출 상태 - NOT_SUBMITTED/SUBMITTED/SUBMITTED_EXTRA/LATE. 서버 판정값(프론트 재계산 금지). 분반 비소속(비소속 관리자)이면 null")
        SubmissionStatus myStatus,

        @Schema(description = "제출 이력 총 건수 - 운영진·관리자에게만 값, 수강생은 null. 과제 삭제 확인 창의 \"제출물 N건 삭제\" 경고가 이 값을 쓴다")
        Integer submissionCount,

        @Schema(description = "자동 채점 문제인가 = 테스트케이스 1개 이상 (judge/design.md 결정 1). 목록 배지·상세 예시 절 표시 여부")
        boolean judgeEnabled
) {
    public static AssignmentResponse of(Assignment assignment, List<TagResponse> tags,
                                        SubmissionStatus myStatus, Integer submissionCount, boolean judgeEnabled) {
        Problem problem = assignment.getProblem();
        return new AssignmentResponse(
                assignment.getId(),
                problem.getId(),
                problem.getProblemNo(),
                assignment.getSessionNo(),
                problem.getTitle(),
                problem.getDescription(),
                tags,
                assignment.getDueAt(),
                assignment.getCreatedAt(),
                myStatus,
                submissionCount,
                judgeEnabled
        );
    }
}
