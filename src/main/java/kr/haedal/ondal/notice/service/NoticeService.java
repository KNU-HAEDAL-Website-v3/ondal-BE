package kr.haedal.ondal.notice.service;

import kr.haedal.ondal.auth.authorization.CohortAuthorizer;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.ForbiddenException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.notice.dto.NoticeCreateRequest;
import kr.haedal.ondal.notice.dto.NoticeResponse;
import kr.haedal.ondal.notice.dto.NoticeUpdateRequest;
import kr.haedal.ondal.notice.entity.Notice;
import kr.haedal.ondal.notice.repository.NoticeRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공지사항 - 전체 공지(관리자)와 분반 공지(운영진 이상). Question 슬라이스 패턴.
 * - 목록은 요청자 의존: 관리자는 전부, 부원은 전체 공지 + 소속 분반 공지 (보관 분반 포함 - 열람은 유지)
 * - 공지 id 는 전역이라 경로에 cohortId 가 없다 → 분반 공지의 "소속자인가 / 운영진인가" 판정은 여기서 CohortAuthorizer 로 (아니면 403)
 * - 쓰기 권한 = 관리 권한: 전체 공지는 관리자, 분반 공지는 그 분반 운영진 이상(관리자 포함). 작성자 여부는 보지 않는다 (design.md 결정 2)
 * - 분반 공지의 쓰기는 보관 분반이면 409 (ensureActive 규약)
 */
@Service
@Transactional
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final CohortRepository cohortRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CohortAuthorizer cohortAuthorizer;
    private final NoticeResponseAssembler assembler;

    public NoticeService(NoticeRepository noticeRepository,
                         CohortRepository cohortRepository,
                         EnrollmentRepository enrollmentRepository,
                         CohortAuthorizer cohortAuthorizer,
                         NoticeResponseAssembler assembler) {
        this.noticeRepository = noticeRepository;
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cohortAuthorizer = cohortAuthorizer;
        this.assembler = assembler;
    }

    /** 내가 볼 수 있는 공지 - 필독 먼저 → 최신순 */
    @Transactional(readOnly = true)
    public List<NoticeResponse> findAllVisible(User viewer) {
        List<Notice> notices = viewer.isAdmin()
                ? noticeRepository.findAllWithAuthor()
                : noticeRepository.findAllVisibleWithAuthor(myCohortIds(viewer));
        return assembler.toResponses(notices, viewer);
    }

    /** 단건 - 분반 공지는 소속자·관리자만 (비소속 403 - 분반 GET 과 같은 규칙) */
    @Transactional(readOnly = true)
    public NoticeResponse findOne(Long noticeId, User viewer) {
        Notice notice = requireNotice(noticeId);
        if (!notice.isGlobal() && !cohortAuthorizer.isAllowed(viewer, notice.getCohort().getId(), EnrollmentRole.STUDENT)) {
            throw new ForbiddenException();
        }
        return assembler.toResponse(notice, viewer);
    }

    /** 전체 공지 등록 - 관리자 (권한은 컨트롤러 @AdminOnly) */
    public NoticeResponse createGlobal(NoticeCreateRequest request, User author) {
        Notice saved = noticeRepository.save(Notice.global(author, request.title(), request.content(), request.pinnedOrFalse()));
        return assembler.toResponse(saved, author);
    }

    /** 분반 공지 등록 - 그 분반 운영진 이상 (권한은 컨트롤러 @CohortRole(OPERATOR)). 보관 분반이면 409 */
    public NoticeResponse createForCohort(Long cohortId, NoticeCreateRequest request, User author) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Notice saved = noticeRepository.save(
                Notice.forCohort(cohort, author, request.title(), request.content(), request.pinnedOrFalse()));
        return assembler.toResponse(saved, author);
    }

    public NoticeResponse update(Long noticeId, NoticeUpdateRequest request, User editor) {
        Notice notice = requireNotice(noticeId);
        requireManage(notice, editor);
        notice.update(request.title(), request.content(), request.pinnedOrFalse());
        return assembler.toResponse(notice, editor);
    }

    public void delete(Long noticeId, User requester) {
        Notice notice = requireNotice(noticeId);
        requireManage(notice, requester);
        noticeRepository.delete(notice);
    }

    /** 전체 공지: 관리자만(403) / 분반 공지: 보관이면 409 → 그 분반 운영진 이상(관리자 포함)이 아니면 403 */
    private void requireManage(Notice notice, User user) {
        if (notice.isGlobal()) {
            if (!user.isAdmin()) {
                throw new ForbiddenException();
            }
            return;
        }
        Cohort cohort = notice.getCohort();
        cohort.ensureActive();
        if (!cohortAuthorizer.isAllowed(user, cohort.getId(), EnrollmentRole.OPERATOR)) {
            throw new ForbiddenException();
        }
    }

    /** 소속 분반 id 목록 - 비어 있으면 어떤 분반과도 맞지 않는 id 하나로 (빈 IN 절 회피) */
    private List<Long> myCohortIds(User viewer) {
        List<Long> ids = enrollmentRepository.findAllByUserIdWithCohort(viewer.getId()).stream()
                .map(e -> e.getCohort().getId())
                .toList();
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    private Notice requireNotice(Long noticeId) {
        return noticeRepository.findByIdWithAuthor(noticeId)
                .orElseThrow(() -> new NotFoundException("공지를 찾을 수 없습니다."));
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }
}
