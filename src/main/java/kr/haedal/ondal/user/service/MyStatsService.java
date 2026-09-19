package kr.haedal.ondal.user.service;

import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import kr.haedal.ondal.user.dto.MyStatsResponse;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 활동 요약 - 본인 제출·정답 수 (2026-09-19 마이페이지 신설).
 * UserService 와 분리한 이유: 제출·채점 리포지토리에 기대는 조회라 계정 관리(승인·목록)와 의존 방향이 다르다.
 */
@Service
@Transactional(readOnly = true)
public class MyStatsService {

    private final SubmissionRepository submissionRepository;
    private final JudgeResultRepository judgeResultRepository;

    public MyStatsService(SubmissionRepository submissionRepository, JudgeResultRepository judgeResultRepository) {
        this.submissionRepository = submissionRepository;
        this.judgeResultRepository = judgeResultRepository;
    }

    public MyStatsResponse statsOf(User me) {
        return new MyStatsResponse(
                me.getCreatedAt(),
                submissionRepository.countByUserIdAndAssignmentIsNotNull(me.getId()),
                submissionRepository.countByUserIdAndProblemIsNotNull(me.getId()),
                judgeResultRepository.findSolvedProblemIdsByUserId(me.getId()).size());
    }
}
