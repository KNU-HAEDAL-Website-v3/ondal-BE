package kr.haedal.ondal.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 테스트마다 모든 테이블을 비운다 (TRUNCATE ... RESTART IDENTITY CASCADE).
 *
 * 테스트 클래스에 @Transactional 을 걸어 롤백하는 방식을 쓰지 않는 이유:
 * MockMvc 요청 전체가 테스트 트랜잭션 안에서 돌아버려서, open-in-view=false 가 드러내야 할
 * LazyInitializationException / N+1 이 테스트에서는 안 보이고 실제 서버에서만 터진다.
 * 요청은 실제와 똑같이 자기 트랜잭션으로 돌게 두고, 뒷정리만 여기서 한다.
 */
@Component
public class DatabaseCleaner {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void clean() {
        List<String> tables = entityManager.getMetamodel().getEntities().stream()
                .map(DatabaseCleaner::tableName)
                .toList();
        entityManager.flush();
        entityManager.createNativeQuery("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE")
                .executeUpdate();
        entityManager.clear();
    }

    /** @Table(name) 이 있으면 그것, 없으면 엔티티 이름 소문자 (이 프로젝트는 전부 @Table 을 명시한다) */
    private static String tableName(EntityType<?> entity) {
        Table table = entity.getJavaType().getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name();
        }
        return entity.getName().toLowerCase();
    }
}
