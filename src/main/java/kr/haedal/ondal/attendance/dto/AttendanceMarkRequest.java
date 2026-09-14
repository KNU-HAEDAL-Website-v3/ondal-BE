package kr.haedal.ondal.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import kr.haedal.ondal.attendance.entity.AttendanceStatus;

import java.util.List;

/**
 * 출석 표시 요청 - 차시 단위 일괄 upsert (docs attendance/design.md 결정 4).
 * 한 명만 바꿔도 원소 1개짜리 목록, "일괄 출석 처리"는 미확인 전원을 PRESENT 로 담은 목록. status null 은 기록 삭제(미확인).
 */
public record AttendanceMarkRequest(
        @Schema(description = "표시할 (수강생 loginId, 판정) 목록 - 같은 loginId 가 여러 번이면 마지막 값")
        @NotEmpty(message = "records는 비어 있을 수 없습니다.")
        List<@Valid Record> records
) {
    public record Record(
            @Schema(description = "수강생 loginId - 이 분반의 STUDENT 여야 한다(아니면 404)")
            @NotBlank(message = "loginId는 비어 있을 수 없습니다.")
            @Size(max = 50, message = "loginId는 50자 이하여야 합니다.")
            String loginId,

            @Schema(description = "PRESENT / LATE / ABSENT, null 이면 기록 삭제(미확인으로)")
            AttendanceStatus status
    ) {
    }
}
