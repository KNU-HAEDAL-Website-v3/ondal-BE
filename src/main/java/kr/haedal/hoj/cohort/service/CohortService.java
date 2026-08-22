package kr.haedal.hoj.cohort.service;

import kr.haedal.hoj.cohort.entity.Cohort;
import kr.haedal.hoj.cohort.entity.CohortStatus;
import kr.haedal.hoj.cohort.repository.CohortRepository;

import kr.haedal.hoj.cohort.dto.CohortCreateRequest;
import kr.haedal.hoj.cohort.dto.CohortResponse;
import kr.haedal.hoj.cohort.dto.CohortUpdateRequest;
import kr.haedal.hoj.common.error.NotFoundException;
import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import kr.haedal.hoj.enrollment.service.EnrollmentService;
import kr.haedal.hoj.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 분반 서비스 — 이후 모든 도메인 서비스의 기준 패턴.
 * - 클래스 레벨 @Transactional, 조회만 readOnly
 * - 응답 DTO는 서비스가 만들어 돌려준다 (컨트롤러는 엔티티를 받지 않는다 — open-in-view=false)
 * - 엔티티 조회 실패는 NotFoundException, 보관 분반 쓰기는 ensureActive()
 */
@Service
@Transactional
public class CohortService {

    private final CohortRepository cohortRepository;
    private final EnrollmentService enrollmentService;
    private final CohortResponseAssembler assembler;

    public CohortService(CohortRepository cohortRepository,
                         EnrollmentService enrollmentService,
                         CohortResponseAssembler assembler) {
        this.cohortRepository = cohortRepository;
        this.enrollmentService = enrollmentService;
        this.assembler = assembler;
    }

    /** 관리자용 전체 목록 (상태별). 기본은 ACTIVE — 보관 분반은 ?status=ARCHIVED 로 따로 본다. */
    @Transactional(readOnly = true)
    public List<CohortResponse> findAll(CohortStatus status, User viewer) {
        return assembler.toResponses(cohortRepository.findAllByStatusOrderByCreatedAtDesc(status), viewer);
    }

    @Transactional(readOnly = true)
    public CohortResponse findOne(Long cohortId, User viewer) {
        return assembler.toResponse(requireCohort(cohortId), viewer);
    }

    /** 생성 + 운영진 동시 지정 (UC-A1). 운영진 지정이 실패(409)하면 분반 생성도 함께 롤백된다. */
    public CohortResponse create(CohortCreateRequest request, User creator) {
        Cohort cohort = cohortRepository.save(Cohort.create(request.name(), request.description()));
        enrollmentService.assign(cohort.getId(), request.operatorLoginIdsOrEmpty(), EnrollmentRole.OPERATOR);
        return assembler.toResponse(cohort, creator);
    }

    public CohortResponse update(Long cohortId, CohortUpdateRequest request, User viewer) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        cohort.update(request.name(), request.description());
        return assembler.toResponse(cohort, viewer);
    }

    public CohortResponse archive(Long cohortId, User viewer) {
        Cohort cohort = requireCohort(cohortId);
        cohort.archive();
        return assembler.toResponse(cohort, viewer);
    }

    public CohortResponse restore(Long cohortId, User viewer) {
        Cohort cohort = requireCohort(cohortId);
        cohort.restore();
        return assembler.toResponse(cohort, viewer);
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }
}
