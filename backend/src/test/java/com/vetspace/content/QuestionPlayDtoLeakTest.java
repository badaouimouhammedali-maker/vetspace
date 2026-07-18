package com.vetspace.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.catalog.dto.QuestionPlayDto;
import com.vetspace.domain.content.Difficulty;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Proves the student-facing question DTO cannot leak correction data: neither the Java type
 * nor its JSON serialization contains truth values or explanations, under any naming convention.
 */
class QuestionPlayDtoLeakTest {

    private static final List<String> FORBIDDEN_FRAGMENTS = List.of("istrue", "is_true", "explanation", "correct");

    @Test
    void serializedJsonContainsNoTruthOrExplanationFields() throws Exception {
        QuestionPlayDto dto = new QuestionPlayDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Quelle est la formule dentaire du chien ?",
            List.of("http://localhost:9000/vetspace/media/x.png"),
            Difficulty.MEDIUM,
            List.of(
                new QuestionPlayDto.PropositionPlayDto(UUID.randomUUID(), "A", "42 dents", 1),
                new QuestionPlayDto.PropositionPlayDto(UUID.randomUUID(), "B", "30 dents", 2)));

        JsonNode json = new ObjectMapper().valueToTree(dto);

        for (String fieldName : collectAllFieldNames(json)) {
            String normalized = fieldName.toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_FRAGMENTS) {
                assertThat(normalized)
                    .as("field '%s' must not carry correction data", fieldName)
                    .doesNotContain(forbidden);
            }
        }
    }

    @Test
    void javaRecordComponentsCarryNoTruthOrExplanationData() {
        assertRecordClean(QuestionPlayDto.class);
        assertRecordClean(QuestionPlayDto.PropositionPlayDto.class);
    }

    private void assertRecordClean(Class<?> recordClass) {
        for (var component : recordClass.getRecordComponents()) {
            String normalized = component.getName().toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_FRAGMENTS) {
                assertThat(normalized)
                    .as("%s.%s must not carry correction data", recordClass.getSimpleName(), component.getName())
                    .doesNotContain(forbidden);
            }
        }
    }

    private List<String> collectAllFieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        collect(node, names);
        return names;
    }

    private void collect(JsonNode node, List<String> names) {
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String name = fields.next();
                names.add(name);
                collect(node.get(name), names);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names));
        }
    }
}
