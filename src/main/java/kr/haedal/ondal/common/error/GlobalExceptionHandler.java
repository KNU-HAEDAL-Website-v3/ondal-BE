package kr.haedal.ondal.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 예외 → HTTP 응답 변환을 한 곳에서.
 * 개별 컨트롤러는 예외를 던지기만 하고, 상태코드/응답모양은 여기서만 정한다.
 *
 * 메시지 규칙:
 * - Unauthenticated/Forbidden: 핸들러 고정 문구 (내부 사정을 노출하지 않음)
 * - NotFound/Conflict/InvalidInput: 예외를 던진 쪽이 준 한국어 문구를 그대로 (자원별로 다르므로)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", "권한이 없습니다."));
    }

    /** 승인 대기 계정 - 403 이지만 코드를 나눠 FE 가 홈 리다이렉트 대신 "승인 대기" 화면을 보이게 한다 (docs 결정 10) */
    @ExceptionHandler(PendingApprovalException.class)
    public ResponseEntity<ErrorResponse> handlePendingApproval(PendingApprovalException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("USER_PENDING", "운영진 승인을 기다리는 계정이에요. 승인이 끝나면 이용할 수 있어요."));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", e.getMessage()));
    }

    /** 외부 구성 요소(채점 엔진) 이용 불가 - 코드는 던진 쪽이 정한다 (JUDGE_UNAVAILABLE). FE 는 "엔진 연결 후 다시" 안내 */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException e) {
        log.warn("서비스 이용 불가: {} - {}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(CohortArchivedException.class)
    public ResponseEntity<ErrorResponse> handleCohortArchived(CohortArchivedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("COHORT_ARCHIVED", e.getMessage()));
    }

    /** 문제 은행 레포(GitHub) 가져오기 실패 - 토큰·레포·네트워크·zip 형식. 메시지에 원인을 담는다 (토큰 값은 없음) */
    @ExceptionHandler(ProblemBankFetchException.class)
    public ResponseEntity<ErrorResponse> handleProblemBankFetch(ProblemBankFetchException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("PROBLEM_BANK_FETCH_FAILED", e.getMessage()));
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(InvalidInputException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", e.getMessage()));
    }

    /** @Valid 실패 - 요청 본문의 필드 검증 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", message));
    }

    /** 경로 변수·쿼리 파라미터 타입 불일치 (예: /api/cohorts/abc, ?status=FOO) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", e.getName() + ": 값의 형식이 올바르지 않습니다."));
    }

    /** 본문이 JSON 이 아니거나 깨진 경우 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", "요청 본문을 읽을 수 없습니다."));
    }

    /** multipart 요청에 필수 파트가 빠진 경우 (예: 제출의 request JSON 파트 누락) */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", "필수 요청 파트가 없습니다: " + e.getRequestPartName()));
    }

    /** 서블릿 multipart 한도(spring.servlet.multipart) 초과 - 서비스 검증과 이중 방어 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_INPUT", "업로드 파일이 허용 크기(20MB)를 초과했습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."));
    }

    /** 매핑되지 않은 경로 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "존재하지 않는 경로입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e); // 원인은 서버 로그에만 - 내부 정보를 응답에 싣지 않는다
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
