package kr.haedal.ondal.qna.repository;

import kr.haedal.ondal.qna.dto.AnswerCount;
import kr.haedal.ondal.qna.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    /** 질문의 답변 - 오래된 순(대화 흐름), 같은 시각은 id asc. 작성자 fetch join (WithXxx + @Query 규약) */
    @Query("select a from Answer a join fetch a.author where a.question.id = :questionId order by a.createdAt asc, a.id asc")
    List<Answer> findAllByQuestionIdWithAuthor(@Param("questionId") Long questionId);

    /** 하위 리소스 스코프 조회 규약 - 경로의 questionId 와 함께 조회, 불일치·부재는 404 */
    @Query("select a from Answer a join fetch a.author where a.id = :id and a.question.id = :questionId")
    Optional<Answer> findByIdAndQuestionIdWithAuthor(@Param("id") Long id, @Param("questionId") Long questionId);

    /** 질문 목록의 답변 수 - 분반 단위 1회 집계 (questionId → count) */
    @Query("select new kr.haedal.ondal.qna.dto.AnswerCount(a.question.id, count(a)) from Answer a "
            + "where a.question.cohort.id = :cohortId group by a.question.id")
    List<AnswerCount> countByCohortIdGroupByQuestion(@Param("cohortId") Long cohortId);

    /** 질문 삭제의 서비스 연쇄 */
    @Modifying
    @Query("delete from Answer a where a.question.id = :questionId")
    void deleteAllByQuestionId(@Param("questionId") Long questionId);
}
