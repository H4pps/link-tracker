package backend.academy.linktracker.scrapper.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RawLinkUpdateEventAvroContractTest {

    private static final String CONTRACT_RELATIVE_PATH = "messaging/src/main/avro/RawLinkUpdateEvent.avsc";

    @Test
    void rawLinkUpdateEventSchemaExistsAndDefinesExpectedFields() throws IOException {
        Path schemaPath = resolveSchemaPath();
        assertThat(schemaPath)
                .as("Expected Avro schema at repository path %s", CONTRACT_RELATIVE_PATH)
                .exists();

        String schema = Files.readString(schemaPath, StandardCharsets.UTF_8);
        assertPattern(schema, "\"type\"\\s*:\\s*\"record\"");
        assertPattern(schema, "\"name\"\\s*:\\s*\"RawLinkUpdateEvent\"");
        assertPattern(schema, "\"name\"\\s*:\\s*\"id\"[\\s\\S]*?\"type\"\\s*:\\s*\"long\"");
        assertPattern(schema, "\"name\"\\s*:\\s*\"url\"[\\s\\S]*?\"type\"\\s*:\\s*\"string\"");
        assertPattern(schema, "\"name\"\\s*:\\s*\"description\"[\\s\\S]*?\"type\"\\s*:\\s*\"string\"");
        assertPattern(schema, "\"name\"\\s*:\\s*\"author\"[\\s\\S]*?\"type\"\\s*:\\s*\"string\"");
        assertPattern(
                schema,
                "\"name\"\\s*:\\s*\"tgChatIds\"[\\s\\S]*?\"type\"\\s*:\\s*\\{[\\s\\S]*?\"type\"\\s*:\\s*\"array\"[\\s\\S]*?\"items\"\\s*:\\s*\"long\"");
    }

    private Path resolveSchemaPath() {
        String basedir = System.getProperty("basedir");
        Path moduleDir = basedir == null
                ? Path.of("").toAbsolutePath()
                : Path.of(basedir).toAbsolutePath();
        Path fromCurrentBaseDir = moduleDir.resolve(CONTRACT_RELATIVE_PATH).normalize();
        if (Files.exists(fromCurrentBaseDir)) {
            return fromCurrentBaseDir;
        }
        Path parentDir = moduleDir.getParent();
        if (parentDir == null) {
            return fromCurrentBaseDir;
        }
        return parentDir.resolve(CONTRACT_RELATIVE_PATH).normalize();
    }

    private void assertPattern(String text, String regex) {
        boolean found = Pattern.compile(regex, Pattern.DOTALL).matcher(text).find();
        assertThat(found).as("Expected schema to match regex: %s", regex).isTrue();
    }
}
