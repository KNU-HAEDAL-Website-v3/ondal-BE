package kr.haedal.ondal.submission.service;

import kr.haedal.ondal.common.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 제출 zip의 로컬 디스크 저장소. 키(submissions/{UUID}.zip)만 밖에 노출한다 -
 * S3 등으로 옮길 때 이 클래스 하나만 교체하면 되도록 (stored_path 열에는 키만 들어간다).
 *
 * 키는 서버가 UUID로 생성한다 - 사용자 입력이 경로에 들어가지 않으므로 경로 탈출(path traversal) 여지가 없다.
 * 원본 파일명은 DB(file_name 열)에만 보관하고 다운로드 시 Content-Disposition으로 복원한다.
 */
@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path root;

    public FileStorage(@Value("${ondal.upload.dir}") String uploadDir) {
        // 상대 경로(transferTo가 서블릿 임시 디렉터리 기준으로 풀어버리는 함정)를 피해 절대 경로로 고정
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** 저장 후 stored_path에 넣을 키를 돌려준다 */
    public String store(MultipartFile file) {
        String key = "submissions/" + UUID.randomUUID() + ".zip";
        Path target = root.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("제출 파일 저장 실패: " + key, e);
        }
        return key;
    }

    public Resource load(String key) {
        Path path = root.resolve(key);
        if (!Files.exists(path)) {
            // DB에는 있는데 디스크에 없는 비정상 상태 - 내부 사정은 감추고 404로
            log.warn("제출 파일이 디스크에 없음: {}", path);
            throw new NotFoundException("제출 파일을 찾을 수 없습니다.");
        }
        return new FileSystemResource(path);
    }

    /** 삭제 실패는 로그만 남기고 진행한다 - 파일 하나 때문에 과제 삭제 전체를 막지 않는다 (고아 파일은 수동 정리) */
    public void delete(String key) {
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException e) {
            log.warn("제출 파일 삭제 실패 - 고아 파일로 남음: {}", key, e);
        }
    }
}
