package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.domain.software.SoftwareApplicationRepository;
import com.iflytek.skillhub.domain.software.SoftwareApplicationStatus;
import com.iflytek.skillhub.domain.software.SoftwareCategory;
import com.iflytek.skillhub.domain.software.SoftwareCategoryRepository;
import com.iflytek.skillhub.repository.SoftwareCatalogQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression guard for the catalog query JPQL: the from-clause is a Java text
 * block, so a missing space after a concatenated "select ..." prefix produced
 * "select a, cfrom ..." (Hibernate syntax error) at runtime only. Executes the
 * real queries against H2 with keyword, category, and status filters — the
 * paths the MockMvc controller tests stub out.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SoftwareCatalogQueryJpqlH2Test {

    @Autowired
    private SoftwareCatalogQueryRepository catalogQueryRepository;
    @Autowired
    private SoftwareApplicationRepository applicationRepository;
    @Autowired
    private SoftwareCategoryRepository categoryRepository;

    @Test
    void searchPublishedApps_executesJpqlWithEveryFilterCombination() {
        seedApp("jpql-demo", "JPQL Demo App", "keyword haystack", true);
        seedCategory("jpql-cat", "JPQL Category");

        var byKeyword = catalogQueryRepository.searchPublishedApps(
                "haystack", null, null, null, PageRequest.of(0, 20));
        assertThat(byKeyword.getContent())
                .extracting(com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse::slug)
                .contains("jpql-demo");

        seedApp("jpql-demo-cat", "JPQL Categorized App", null, true, "jpql-cat");
        var byCategory = catalogQueryRepository.searchPublishedApps(
                null, "jpql-cat", null, null, PageRequest.of(0, 20));
        assertThat(byCategory.getContent())
                .extracting(com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse::slug)
                .contains("jpql-demo-cat");

        // the count query shares the same concatenation and must parse too
        var unfiltered = catalogQueryRepository.searchPublishedApps(
                null, null, null, null, PageRequest.of(0, 20));
        assertThat(unfiltered.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void searchBySlug_matchesOnBothPortalAndAdminQueries() {
        seedApp("jpql-slug-target", "Unrelated Display Name", "unrelated words", true);
        seedApp("jpql-admin-slug-target", "Other Display Name", "other words", false);

        var portalBySlug = catalogQueryRepository.searchPublishedApps(
                "jpql-slug-target", null, null, null, PageRequest.of(0, 20));
        assertThat(portalBySlug.getContent())
                .extracting(com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse::slug)
                .containsExactly("jpql-slug-target");

        var adminBySlug = catalogQueryRepository.searchAppsForAdmin(
                "jpql-admin-slug-target", null, null, PageRequest.of(0, 20));
        assertThat(adminBySlug.getContent())
                .extracting(com.iflytek.skillhub.dto.software.SoftwareAppAdminSummaryResponse::slug)
                .containsExactly("jpql-admin-slug-target");
    }

    @Test
    void searchAppsForAdmin_executesJpqlWithKeywordAndStatus() {
        seedApp("jpql-admin", "JPQL Admin App", "admin haystack", false);

        var byKeyword = catalogQueryRepository.searchAppsForAdmin(
                "haystack", null, null, PageRequest.of(0, 20));
        assertThat(byKeyword.getContent())
                .extracting(com.iflytek.skillhub.dto.software.SoftwareAppAdminSummaryResponse::slug)
                .contains("jpql-admin");

        var byStatus = catalogQueryRepository.searchAppsForAdmin(
                null, null, SoftwareApplicationStatus.DRAFT, PageRequest.of(0, 20));
        assertThat(byStatus.getContent())
                .extracting(com.iflytek.skillhub.dto.software.SoftwareAppAdminSummaryResponse::slug)
                .contains("jpql-admin");

        var unfiltered = catalogQueryRepository.searchAppsForAdmin(
                null, null, null, PageRequest.of(0, 20));
        assertThat(unfiltered.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    private void seedApp(String slug, String name, String description, boolean published) {
        seedApp(slug, name, description, published, null);
    }

    private void seedApp(String slug, String name, String description, boolean published, String categoryCode) {
        SoftwareApplication app = new SoftwareApplication(slug, name, description, null, categoryCode, "jpql-test");
        if (published) {
            app.publish();
        }
        applicationRepository.save(app);
    }

    private void seedCategory(String code, String name) {
        SoftwareCategory category = new SoftwareCategory(code, name, 99, "jpql-test");
        categoryRepository.save(category);
    }
}
