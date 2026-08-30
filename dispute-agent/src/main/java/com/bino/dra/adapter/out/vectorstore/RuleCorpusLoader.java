package com.bino.dra.adapter.out.vectorstore;

import com.bino.dra.adapter.out.support.Resources;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RuleCorpusLoader {

    public static final String META_RULE_ID = "ruleId";
    public static final String META_NETWORK = "network";
    public static final String META_REASON_CODE = "reasonCode";

    public static final String META_APPLIES_TO = "appliesTo";

    public static final String META_TITLE = "title";
    public static final String META_SECTION = "section";

    public static final String META_CHUNK_ID = "chunkId";

    public static final String ANY = "ANY";

    public static final String CORPUS_LOCATION = "classpath:rules/*.md";

    private static final String FRONT_MATTER_DELIMITER = "---";
    private static final String SECTION_PREFIX = "## ";
    private static final int MAX_SLUG_LENGTH = 48;

    private RuleCorpusLoader() {
    }

    public static List<Document> load(Resource[] sheets) {
        if (sheets == null || sheets.length == 0) {
            throw new IllegalStateException("Empty rule corpus: no sheet found under classpath:rules/*.md");
        }
        Map<String, Document> byId = new LinkedHashMap<>();
        for (Resource sheet : sheets) {
            String markdown = Resources.text(sheet, "rule sheet");
            for (Document chunk : parse(markdown, sheet.getFilename())) {
                String id = RuleChunks.chunkId(chunk);
                if (byId.putIfAbsent(id, chunk) != null) {
                    throw new IllegalStateException("Duplicate chunk id in corpus: " + id);
                }
            }
        }
        return List.copyOf(byId.values());
    }

    private static String uuidOf(String chunkId) {
        return UUID.nameUUIDFromBytes(chunkId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static List<Document> parse(String markdown, String sourceName) {
        String[] parts = splitFrontMatter(markdown, sourceName);
        Map<String, String> header = readFrontMatter(parts[0], sourceName);

        String ruleId = required(header, META_RULE_ID, sourceName);
        String network = required(header, META_NETWORK, sourceName);
        String reasonCode = required(header, META_REASON_CODE, sourceName);
        String title = required(header, META_TITLE, sourceName);
        String appliesTo = normaliseCodes(header.getOrDefault(META_APPLIES_TO, ""));

        List<Document> chunks = new ArrayList<>();
        for (Section section : splitSections(parts[1])) {
            String chunkId = ruleId + "#" + slug(section.title());
            chunks.add(Document.builder()
                    .id(uuidOf(chunkId))
                    .text(enrich(network, reasonCode, title, section))
                    .metadata(Map.of(
                            META_CHUNK_ID, chunkId,
                            META_RULE_ID, ruleId,
                            META_NETWORK, network,
                            META_REASON_CODE, reasonCode,
                            META_APPLIES_TO, appliesTo,
                            META_TITLE, title,
                            META_SECTION, section.title()))
                    .build());
        }
        if (chunks.isEmpty()) {
            throw new IllegalStateException("Rule sheet without any '## ' section: " + sourceName);
        }
        return chunks;
    }

    static String enrich(String network, String reasonCode, String title, Section section) {
        String provenance = ANY.equals(reasonCode)
                ? network + " - " + title
                : network + " reason code " + reasonCode + " - " + title;
        return provenance + "\n" + section.title()
                + RuleChunks.BODY_SEPARATOR + section.body();
    }

    record Section(String title, String body) {
    }

    private static String[] splitFrontMatter(String markdown, String sourceName) {
        String normalised = markdown.replace("\r\n", "\n").stripLeading();
        if (!normalised.startsWith(FRONT_MATTER_DELIMITER)) {
            throw new IllegalStateException("Missing YAML front matter: " + sourceName);
        }
        int end = normalised.indexOf("\n" + FRONT_MATTER_DELIMITER, FRONT_MATTER_DELIMITER.length());
        if (end < 0) {
            throw new IllegalStateException("Unterminated YAML front matter: " + sourceName);
        }
        return new String[] {
                normalised.substring(FRONT_MATTER_DELIMITER.length(), end),
                normalised.substring(end + 1 + FRONT_MATTER_DELIMITER.length())};
    }

    // Hand-parsed: SnakeYAML would coerce a reason code like "10.4" to a Double
    private static Map<String, String> readFrontMatter(String header, String sourceName) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : header.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator < 0) {
                throw new IllegalStateException(
                        "Front matter line without ':' in " + sourceName + ": " + trimmed);
            }
            String key = trimmed.substring(0, separator).strip();
            String value = trimmed.substring(separator + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    private static List<Section> splitSections(String body) {
        List<Section> sections = new ArrayList<>();
        String currentTitle = null;
        StringBuilder currentBody = new StringBuilder();

        for (String line : body.split("\n")) {
            if (line.startsWith(SECTION_PREFIX)) {
                addIfComplete(sections, currentTitle, currentBody);
                currentTitle = line.substring(SECTION_PREFIX.length()).strip();
                currentBody = new StringBuilder();
            } else if (currentTitle != null) {
                currentBody.append(line).append('\n');
            }
        }
        addIfComplete(sections, currentTitle, currentBody);
        return sections;
    }

    private static void addIfComplete(List<Section> sections, String title, StringBuilder body) {
        if (title == null) {
            return;
        }
        String text = body.toString().strip();
        if (text.isEmpty()) {
            throw new IllegalStateException("Empty section in corpus: '" + title + "'");
        }
        sections.add(new Section(title, text));
    }

    static String normaliseCodes(String list) {
        if (list == null || list.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(",");
        for (String code : list.split(",")) {
            String clean = code.strip();
            if (!clean.isEmpty()) {
                sb.append(clean).append(',');
            }
        }
        return sb.length() == 1 ? "" : sb.toString();
    }

    public static boolean applies(String appliesTo, String reasonCode) {
        return appliesTo != null && !appliesTo.isEmpty()
                && reasonCode != null && !reasonCode.isEmpty()
                && appliesTo.contains("," + reasonCode + ",");
    }

    static String slug(String title) {
        String base = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return base.length() <= MAX_SLUG_LENGTH ? base : base.substring(0, MAX_SLUG_LENGTH);
    }

    private static String required(Map<String, String> header, String key, String sourceName) {
        String value = header.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing or empty front matter key: '" + key + "' in " + sourceName);
        }
        return value;
    }
}
