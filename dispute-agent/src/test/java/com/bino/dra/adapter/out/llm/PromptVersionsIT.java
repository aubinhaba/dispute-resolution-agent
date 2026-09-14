package com.bino.dra.adapter.out.llm;

import com.bino.dra.testsupport.NoDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

// The two properties that make a promotion safe: the label derives from the pin, and the file
// the pin names exists. Written twice, the version could name a prompt nobody is running
@NoDatabase
@SpringBootTest(properties = "spring.ai.anthropic.api-key=not-used-by-this-test")
class PromptVersionsIT {

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${dra.prompts.decision}")
    private String decisionVersion;

    @Value("${dra.prompts.evidence}")
    private String evidenceVersion;

    @Value("${dra.prompts.rerank}")
    private String rerankVersion;

    @Value("${dra.agent.decision-version}")
    private String decisionLabel;

    @Value("${dra.agent.evidence-version}")
    private String evidenceLabel;

    @Test
    void the_audit_label_derives_from_the_pin_so_the_two_cannot_diverge() {
        assertThat(decisionLabel).isEqualTo("decision-llm@" + decisionVersion);
        assertThat(evidenceLabel).isEqualTo("evidence-llm@" + evidenceVersion);
    }

    @Test
    void every_pin_names_a_prompt_file_that_exists() {
        assertThat(prompt("decision/decide." + decisionVersion)).isTrue();
        assertThat(prompt("evidence/gather." + evidenceVersion)).isTrue();
        assertThat(prompt("compliance/rerank." + rerankVersion)).isTrue();
    }

    private boolean prompt(String path) {
        return resourceLoader.getResource("classpath:prompts/" + path + ".md").exists();
    }
}
