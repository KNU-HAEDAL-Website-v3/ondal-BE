package kr.haedal.ondal.problem.service;

import kr.haedal.ondal.common.error.ConflictException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.problem.dto.TagPayload;
import kr.haedal.ondal.problem.dto.TagResponse;
import kr.haedal.ondal.problem.entity.Tag;
import kr.haedal.ondal.problem.repository.ProblemRepository;
import kr.haedal.ondal.problem.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 문제 태그 어휘 관리 - 조회는 누구나(출제 화면의 선택지), 등록·수정·삭제는 전역 ADMIN (docs permissions.md).
 *
 * 만드는 권한을 좁게 둔 이유: 운영진이 자유로 만들면 "DP / 다이나믹프로그래밍 / dp" 로 갈라져 분류가 쓸모없어진다.
 * 필요한 태그가 없으면 관리자에게 요청하는 흐름 (2026-09-15 PM 결정).
 */
@Service
@Transactional
public class TagService {

    private final TagRepository tagRepository;
    private final ProblemRepository problemRepository;

    public TagService(TagRepository tagRepository, ProblemRepository problemRepository) {
        this.tagRepository = tagRepository;
        this.problemRepository = problemRepository;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> findAll() {
        return tagRepository.findAllByOrderByNameAsc().stream().map(TagResponse::of).toList();
    }

    public TagResponse create(TagPayload payload) {
        String name = payload.name().strip();
        if (tagRepository.existsByName(name)) {
            throw new ConflictException("이미 있는 태그입니다: " + name);
        }
        return TagResponse.of(tagRepository.save(Tag.create(name)));
    }

    /** 이름만 바꾼다 - 붙어 있던 문제 연결은 그대로라, 표기 통일(오타 수정)이 곧 전체 반영이다 */
    public TagResponse update(Long tagId, TagPayload payload) {
        Tag tag = requireTag(tagId);
        String name = payload.name().strip();
        if (tagRepository.existsByNameAndIdNot(name, tagId)) {
            throw new ConflictException("이미 있는 태그입니다: " + name);
        }
        tag.rename(name);
        return TagResponse.of(tag);
    }

    /**
     * 삭제 - 쓰는 문제가 하나라도 있으면 409.
     * 조용히 연결을 끊으면 분류가 소리 없이 사라진다 - 먼저 문제에서 떼도록 알린다.
     */
    public void delete(Long tagId) {
        Tag tag = requireTag(tagId);
        long used = problemRepository.countByTagId(tagId);
        if (used > 0) {
            throw new ConflictException("이 태그를 쓰는 문제가 " + used + "개 있습니다. 먼저 문제에서 태그를 떼세요.");
        }
        tagRepository.delete(tag);
    }

    private Tag requireTag(Long tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new NotFoundException("태그를 찾을 수 없습니다."));
    }
}
