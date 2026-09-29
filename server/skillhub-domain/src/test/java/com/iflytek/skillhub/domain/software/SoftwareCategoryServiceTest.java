package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SoftwareCategoryServiceTest {

    private final SoftwareCategoryRepository categoryRepository = mock(SoftwareCategoryRepository.class);
    private final SoftwareApplicationRepository applicationRepository =
            mock(SoftwareApplicationRepository.class);
    private final SoftwareCategoryService service =
            new SoftwareCategoryService(categoryRepository, applicationRepository);

    @Test
    void create_normalizesCodeAndRejectsDuplicate() {
        when(categoryRepository.countByCode("dev-tools")).thenReturn(0L);
        when(categoryRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of());
        when(categoryRepository.save(org.mockito.ArgumentMatchers.any(SoftwareCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoftwareCategory category = service.create("  Dev-Tools ", "开发工具", 1, "admin-1");

        assertThat(category.getCode()).isEqualTo("dev-tools");
        assertThat(category.getName()).isEqualTo("开发工具");

        when(categoryRepository.countByCode("dev-tools")).thenReturn(1L);
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.create("dev-tools", "开发工具", 1, "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.category.duplicate");
    }

    @Test
    void delete_refusesCategoryStillReferencedByApplications() {
        SoftwareCategory category = new SoftwareCategory("dev-tools", "开发工具", 1, "a");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(applicationRepository.existsByCategoryCode("dev-tools")).thenReturn(true);

        DomainConflictException ex = assertThrows(DomainConflictException.class,
                () -> service.delete(1L));
        assertThat(ex.messageCode()).isEqualTo("error.software.category.inUse");
        assertThat(ex.statusCode()).isEqualTo(409);
    }

    @Test
    void create_requiresName() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.create("dev-tools", "  ", 1, "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.category.nameRequired");
    }
}
