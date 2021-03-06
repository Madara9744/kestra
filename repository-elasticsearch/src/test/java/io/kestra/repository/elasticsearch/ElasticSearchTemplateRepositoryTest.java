package io.kestra.repository.elasticsearch;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.AfterEach;
import io.kestra.core.repositories.AbstractTemplateRepositoryTest;

import java.io.IOException;
import jakarta.inject.Inject;

@MicronautTest
class ElasticSearchTemplateRepositoryTest extends AbstractTemplateRepositoryTest {
    @Inject
    ElasticSearchTemplateRepository elasticSearchTemplateRepository;

    @Inject
    private ElasticSearchRepositoryTestUtils utils;

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticSearchTemplateRepository.initMapping();
    }
}
