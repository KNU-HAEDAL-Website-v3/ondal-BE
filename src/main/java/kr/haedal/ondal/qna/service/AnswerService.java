package kr.haedal.ondal.qna.service;

import kr.haedal.ondal.auth.authorization.CohortAuthorizer;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.ForbiddenException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.qna.dto.AnswerCreateRequest;
import kr.haedal.ondal.qna.dto.AnswerResponse;
import kr.haedal.ondal.qna.dto.AnswerUpdateRequest;
import kr.haedal.ondal.qna.entity.Answer;
import kr.haedal.ondal.qna.entity.Question;
import kr.haedal.ondal.qna.repository.AnswerRepository;
import kr.haedal.ondal.qna.repository.QuestionRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Q&A 답변 CRUD - Question 슬라이스와 같은 규칙 (docs qna/design.md 결정 11).
 * - 스코프 조회 체인: 분반 → 질문(findByIdAndCohortId) → 답변(findByIdAndQuestionId) - 불일치·부재는 404
 * - 쓰기는 cohort.ensureActive() - 보관 분반이면 409
 * - 등록은 분반 소속 누구나(어노테이션), 수정은 작성자만, 삭제는 작성자 또는 운영진 이상 - 여기서 판정, 아니면 403
 */
@Service
@Transactional
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final CohortRepository cohortRepository;
    private final CohortAuthorizer cohortAuthorizer;
    private final AnswerResponseAssembler assembler;

    public AnswerService(AnswerRepository answerRepository,
                         QuestionRepository questionRepository,
                         CohortRepository cohortRepository,
                         CohortAuthorizer cohortAuthorizer,
                         AnswerResponseAssembler assembler) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.cohortRepository = cohortRepository;
        this.cohortAuthorizer = cohortAuthorizer;
        this.assembler = assembler;
    }

    /** 목록 - 오래된 순(대화 흐름). 보관 분반도 열람 유지 */
    @Transactional(readOnly = true)
    public List<AnswerResponse> findAll(Long cohortId, Long questionId, User viewer) {
        Cohort cohort = requireCohort(cohortId);
        requireQuestion(cohortId, questionId);
        return assembler.toResponses(answerRepository.findAllByQuestionIdWithAuthor(questionId), cohort, viewer);
    }

    public AnswerResponse create(Long cohortId, Long questionId, AnswerCreateRequest request, User author) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Question question = requireQuestion(cohortId, questionId);
        Answer saved = answerRepository.save(Answer.create(question, author, request.content()));
        return assembler.toResponse(saved, cohort, author);
    }

    /** 수정 - 작성자 본인만 (운영진·관리자도 남의 답변은 고칠 수 없다) */
    public AnswerResponse update(Long cohortId, Long questionId, Long answerId, AnswerUpdateRequest request, User editor) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        requireQuestion(cohortId, questionId);
        Answer answer = requireAnswer(questionId, answerId);
        if (!answer.isWrittenBy(editor)) {
            throw new ForbiddenException();
        }
        answer.update(request.content());
        return assembler.toResponse(answer, cohort, editor);
    }

    /** 삭제 - 작성자 본인 또는 운영진 이상 */
    public void delete(Long cohortId, Long questionId, Long answerId, User requester) {
        requireCohort(cohortId).ensureActive();
        requireQuestion(cohortId, questionId);
        Answer answer = requireAnswer(questionId, answerId);
        if (!answer.isWrittenBy(requester)
                && !cohortAuthorizer.isAllowed(requester, cohortId, EnrollmentRole.OPERATOR)) {
            throw new ForbiddenException();
        }
        answerRepository.delete(answer);
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }

    private Question requireQuestion(Long cohortId, Long questionId) {
        return questionRepository.findByIdAndCohortIdWithAuthor(questionId, cohortId)
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
    }

    private Answer requireAnswer(Long questionId, Long answerId) {
        return answerRepository.findByIdAndQuestionIdWithAuthor(answerId, questionId)
                .orElseThrow(() -> new NotFoundException("답변을 찾을 수 없습니다."));
    }
}
