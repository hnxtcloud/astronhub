package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityType;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillCatalogQueryAdapterTest {
    @Mock
    private SkillSearchAppService skillSearchAppService;

    private SkillCatalogQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SkillCatalogQueryAdapter(skillSearchAppService);
    }

    @Test
    void shouldAdaptExistingSkillSearchWithoutChangingItsService() {
        SkillSummaryResponse skill = new SkillSummaryResponse(
                7L, "lint", "Lint Skill", "Checks source files", "PUBLIC", "ACTIVE",
                12L, 2, null, 0, "global", Instant.parse("2026-01-03T12:00:00Z"),
                false, new SkillLifecycleVersionResponse(70L, "1.2.0", "PUBLISHED"),
                null, null, "PUBLISHED", null);
        given(skillSearchAppService.search(
                "lint", null, "newest", 0, 20, List.of(), "user-1", Map.of()))
                .willReturn(new SkillSearchAppService.SearchResponse(List.of(skill), 1, 0, 20));

        var page = adapter.findSkillsForCatalog("lint", 0, 20, "user-1", Map.of());

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(CapabilityType.SKILL);
            assertThat(item.coordinate()).isEqualTo("skill:@global/lint");
            assertThat(item.version()).isEqualTo("1.2.0");
            assertThat(item.primaryMetric()).isEqualTo(12L);
        });
    }
}
