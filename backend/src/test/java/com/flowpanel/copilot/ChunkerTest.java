package com.flowpanel.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    @Test
    void splitsOnHeadingsFirst() {
        List<Chunker.Chunk> chunks = Chunker.chunk("# Title\n\nIntro text.\n\n## A\n\nAlpha rules.\n\n## B\n\nBeta rules.");
        assertThat(chunks).extracting(Chunker.Chunk::heading).containsExactly("Title", "A", "B");
        assertThat(chunks.get(1).content()).isEqualTo("A\nAlpha rules.");
    }

    @Test
    void longSectionsAreSplitWithOverlap() {
        StringBuilder body = new StringBuilder("## Long\n\n");
        for (int i = 0; i < 60; i++) {
            body.append("Sentence number ").append(i).append(" explains a rule in some detail for the test. ");
            if (i % 6 == 5) {
                body.append("\n\n");
            }
        }
        List<Chunker.Chunk> chunks = Chunker.chunk(body.toString(), 200, 30);
        assertThat(chunks).hasSizeGreaterThan(2);
        chunks.forEach(c -> assertThat(c.tokens()).isLessThanOrEqualTo(260));
        String endOfFirst = chunks.get(0).content().substring(chunks.get(0).content().length() - 60);
        String overlapWord = endOfFirst.substring(endOfFirst.lastIndexOf("number"));
        assertThat(chunks.get(1).content()).contains(overlapWord.substring(0, 12));
    }

    @Test
    void verifyKeepsOnlyCitationsToRetrievedPassages() {
        CopilotService.Verified v = CopilotService.verify("Bonus is 25 % [1]. Rest is 11 h [7].", 2);
        assertThat(v.valid()).containsExactly(1);
        assertThat(v.text()).isEqualTo("Bonus is 25 % [1]. Rest is 11 h.");
        assertThat(CopilotService.verify("No sources [3]", 0).valid()).isEmpty();
        assertThat(CopilotService.verify(CopilotService.NOT_FOUND, 3).valid()).isEmpty();
    }
}
