package kr.haedal.ondal.submission.dto;

import org.springframework.core.io.Resource;

/** 파일 다운로드(#21) 반환 묶음 - 컨트롤러가 Content-Disposition에 원본 파일명을 실어야 해서 Resource와 함께 넘긴다 */
public record SubmissionFile(Resource resource, String fileName) {
}
