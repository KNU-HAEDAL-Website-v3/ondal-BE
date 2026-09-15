package kr.haedal.ondal.problem.dto;

import kr.haedal.ondal.problem.entity.Tag;

/** 태그 - 목록·문제 응답·선택지가 전부 이 모양 */
public record TagResponse(Long id, String name) {

    public static TagResponse of(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
