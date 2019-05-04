package cz.cvut.spipes.modules;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ImportE5XZIPModuleTest extends ImportE5XModuleTest {

    private static Stream<Arguments> generateTestData() {
        String dir = "data/e5x.zip";
        String contentType = "application/octet-stream";

        List<String> files = null;
        try {
            files = IOUtils.readLines(
                Objects.requireNonNull(ImportE5XZIPModuleTest.class.getClassLoader().getResourceAsStream(dir)),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Objects.requireNonNull(files)
            .stream().map((file) -> Arguments.of("/" + dir + "/" + file, contentType));
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("generateTestData")
    public void execute(String path, String contentType) {
        super.execute(path, contentType);
    }
}