package kr.haedal.ondal.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.authorization.AdminOnly;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.problem.dto.TagPayload;
import kr.haedal.ondal.problem.dto.TagResponse;
import kr.haedal.ondal.problem.service.TagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 문제 태그 API (#57~#60) - 조회는 누구나(출제 화면의 선택지·목록 필터), 등록·수정·삭제는 전역 ADMIN.
 * 어휘 관리를 관리자로 좁힌 이유는 TagService 주석 참고 (표기 갈라짐 방지).
 */
@Tag(name = "Tag", description = "문제 태그 - 조회는 누구나, 등록·수정·삭제는 관리자")
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "태그 목록 - 이름순")
    @LoginOnly
    @GetMapping
    public List<TagResponse> list() {
        return tagService.findAll();
    }

    @Operation(summary = "[관리자] 태그 등록 - 이름 중복이면 409")
    @AdminOnly
    @PostMapping
    public ResponseEntity<TagResponse> create(@RequestBody @Valid TagPayload request) {
        TagResponse created = tagService.create(request);
        return ResponseEntity.created(URI.create("/api/tags/" + created.id())).body(created);
    }

    @Operation(summary = "[관리자] 태그 이름 수정 - 붙어 있던 문제 연결은 그대로라 표기 통일이 전체에 반영된다")
    @AdminOnly
    @PutMapping("/{tagId}")
    public TagResponse update(@PathVariable Long tagId, @RequestBody @Valid TagPayload request) {
        return tagService.update(tagId, request);
    }

    @Operation(summary = "[관리자] 태그 삭제 - 이 태그를 쓰는 문제가 있으면 409")
    @AdminOnly
    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long tagId) {
        tagService.delete(tagId);
    }
}
