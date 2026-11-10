package com.gradeup.config;

import com.gradeup.ai.AiClient;
import com.gradeup.ai.GroqAiClient;
import com.gradeup.ai.OfflineAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks which AI engine the app uses when it starts.
 *
 *   - GROQ_API_KEY is set   -> use GroqAiClient (the real AI service on the internet).
 *   - GROQ_API_KEY is empty -> use OfflineAiClient (simple rules, works with no internet).
 *
 * This is the ONLY place that makes this choice. Every other class just asks Spring for
 * "an AiClient" and gets whichever one was created here (see AiClient.java for why).
 */
@Configuration
public class AiConfig {

    /** A logger writes messages to the console while the app runs. */
    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiClient aiClient(GradeUpProperties properties) {
        GradeUpProperties.Ai aiSettings = properties.getAi();

        if (aiSettings.isConfigured()) {
            log.info("AI provider enabled: Groq model '{}'", aiSettings.getModel());
            return new GroqAiClient(aiSettings);
        }

        log.info("No AI API key configured (set GROQ_API_KEY to enable). "
                + "GradeUp is running with the built-in offline study engine — "
                + "summarization and question generation remain fully available.");
        return new OfflineAiClient();
    }
}
