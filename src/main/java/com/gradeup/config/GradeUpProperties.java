package com.gradeup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Our app's settings, read from application.yml.
 *
 * HOW IT WORKS
 * @ConfigurationProperties(prefix = "gradeup") tells Spring: "take everything under
 * 'gradeup:' in application.yml and copy it into this object". For example:
 *
 *     gradeup:
 *       storage:
 *         location: ./storage/handouts     ->  getStorage().getLocation()
 *       ai:
 *         api-key: ${GROQ_API_KEY:}        ->  getAi().getApiKey()
 *
 * (Spring turns "api-key" in the file into "apiKey" in Java automatically.)
 * The values written below are the defaults, used if application.yml doesn't set them.
 *
 * Settings are grouped into three small classes: Storage, Upload and Ai.
 */
@ConfigurationProperties(prefix = "gradeup")
public class GradeUpProperties {

    private Storage storage = new Storage();
    private Upload upload = new Upload();
    private Ai ai = new Ai();

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public Upload getUpload() { return upload; }
    public void setUpload(Upload upload) { this.upload = upload; }

    public Ai getAi() { return ai; }
    public void setAi(Ai ai) { this.ai = ai; }

    /** Where uploaded PDFs are saved on disk. */
    public static class Storage {
        private String location = "./storage/handouts";

        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
    }

    /** Upload limits. 26_214_400 bytes = 25 MB (the _ is just to make big numbers readable). */
    public static class Upload {
        private long maxSizeBytes = 26_214_400L;

        public long getMaxSizeBytes() { return maxSizeBytes; }
        public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
    }

    /** Settings for the Groq AI service. Without an API key, the offline engine is used instead. */
    public static class Ai {
        private String provider = "groq";
        private String apiKey = "";
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String model = "llama-3.3-70b-versatile";
        private int timeoutSeconds = 45;
        /** Long handouts are cut to this many characters before being sent to the AI. */
        private int maxInputChars = 12_000;

        /** True only if an API key has been set. */
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public int getMaxInputChars() { return maxInputChars; }
        public void setMaxInputChars(int maxInputChars) { this.maxInputChars = maxInputChars; }
    }
}
