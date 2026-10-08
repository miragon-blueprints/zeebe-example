package io.miragon.blueprint.adapter.outbound.db;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@Import(LeasingApplicationPersistenceAdapter.class)
class LeasingApplicationPersistenceAdapterTest {

    @Autowired
    private LeasingApplicationPersistenceAdapter underTest;

    @Autowired
    private TestEntityManager entityManager;

    private final ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    @Test
    @DisplayName("saves and reloads an application")
    void savesAndReloadsAnApplication() {
        // given: a leasing application
        LeasingApplication application = testLeasingApplication().id(id).build();

        // when: it is saved and re-read from a cleared persistence context
        LeasingApplication saved = underTest.save(application);
        entityManager.flush();
        entityManager.clear();

        // then: both the returned and the reloaded application equal the original
        assertThat(saved).usingRecursiveComparison().isEqualTo(application);
        assertThat(underTest.findById(id)).get().usingRecursiveComparison().isEqualTo(application);
    }

    @Test
    @DisplayName("findById returns null when the application does not exist")
    void findByIdReturnsNullWhenTheApplicationDoesNotExist() {
        // given: an empty database
        // when / then: the lookup returns null
        assertThat(underTest.findById(id)).isEmpty();
    }

    @Test
    @Sql("classpath:sql/leasing-application.sql")
    @DisplayName("findById maps an existing row back to the domain")
    void findByIdMapsAnExistingRowBackToTheDomain() {
        // given: a pre-inserted row (see sql/leasing-application.sql)
        // when / then: the adapter maps it back to the expected application
        assertThat(underTest.findById(id))
            .get()
            .usingRecursiveComparison()
            .isEqualTo(testLeasingApplication().id(id).build());
    }

    @Test
    @DisplayName("findAll returns a page ordered by creation date, newest first")
    void findAllReturnsAPageOrderedByCreationDateNewestFirst() {
        // given: three applications created on different days
        LeasingApplication older = testLeasingApplication()
            .id(new ApplicationId(UUID.randomUUID()))
            .createdAt(LocalDateTime.of(2026, 1, 1, 8, 0))
            .build();
        LeasingApplication newer = testLeasingApplication()
            .id(new ApplicationId(UUID.randomUUID()))
            .createdAt(LocalDateTime.of(2026, 3, 1, 8, 0))
            .build();
        LeasingApplication newest = testLeasingApplication()
            .id(new ApplicationId(UUID.randomUUID()))
            .createdAt(LocalDateTime.of(2026, 6, 1, 8, 0))
            .build();
        List.of(older, newest, newer).forEach(underTest::save);
        entityManager.flush();
        entityManager.clear();

        // when: the first page is read without a filter
        LeasingApplicationRepository.Page page =
            underTest.findAll(new LeasingApplicationRepository.Criteria(null, 0, 10));

        // then: all three come back, newest first
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items())
            .extracting(LeasingApplication::id)
            .containsExactly(newest.id(), newer.id(), older.id());
    }

    @Test
    @DisplayName("findAll filters by status and paginates")
    void findAllFiltersByStatusAndPaginates() {
        // given: two RECEIVED and one ACTIVE application
        underTest.save(
            testLeasingApplication().id(new ApplicationId(UUID.randomUUID())).status(LeasingStatus.RECEIVED).build());
        underTest.save(
            testLeasingApplication().id(new ApplicationId(UUID.randomUUID())).status(LeasingStatus.RECEIVED).build());
        underTest.save(
            testLeasingApplication().id(new ApplicationId(UUID.randomUUID())).status(LeasingStatus.ACTIVE).build());
        entityManager.flush();
        entityManager.clear();

        // when: only ACTIVE applications are requested
        LeasingApplicationRepository.Page page =
            underTest.findAll(new LeasingApplicationRepository.Criteria(LeasingStatus.ACTIVE, 0, 10));

        // then: only the single ACTIVE application is returned
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().status()).isEqualTo(LeasingStatus.ACTIVE);
    }
}
