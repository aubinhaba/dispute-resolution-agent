package com.bino.dra.adapter.out.vectorstore;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEmbeddingModelTest {

    private static final int ALL_MINILM_L6_V2_DIMENSIONS = 384;

    private static TransformersEmbeddingModel embeddingModel;

    @BeforeAll
    static void loadModel() throws Exception {
        embeddingModel = new TransformersEmbeddingModel();
        embeddingModel.afterPropertiesSet();
    }

    @Test
    void local_model_produces_384_dimensional_vectors() {
        float[] vector = embeddingModel.embed("Visa reason code 10.4 covers card-absent fraud.");

        assertThat(vector).hasSize(ALL_MINILM_L6_V2_DIMENSIONS);
    }

    @Test
    void the_same_text_produces_exactly_the_same_vector() {
        String text = "The issuer may raise a dispute when the cardholder denies authorising the transaction.";

        float[] first = embeddingModel.embed(text);
        float[] second = embeddingModel.embed(text);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void two_texts_from_the_same_domain_are_closer_than_two_unrelated_ones() {
        float[] fraud = embeddingModel.embed("The cardholder denies authorising this card-absent purchase.");
        float[] nearbyFraud = embeddingModel.embed("The transaction was not authorised by the legitimate cardholder.");
        float[] offTopic = embeddingModel.embed("Sourdough bread needs a long cold fermentation.");

        assertThat(cosineSimilarity(fraud, nearbyFraud))
                .isGreaterThan(cosineSimilarity(fraud, offTopic));
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
