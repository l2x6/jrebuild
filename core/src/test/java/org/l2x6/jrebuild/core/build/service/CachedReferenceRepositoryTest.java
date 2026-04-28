package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.core.build.Resource;
import org.l2x6.pom.tuner.model.Gavtc;

public class CachedReferenceRepositoryTest {

    @Test
    void get() {
        Path localCacheDir = Path.of("target/CachedReferenceRepositoryTest + " + UUID.randomUUID());
        String referenceRepobaseUri = "http://localhost:8080/fake";
        BiConsumer<String, Path> getFile = (url, path) -> {
            try {
                Files.write(path, url.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not write to " + path, e);
            }
        };
        CachedReferenceRepository repo = new CachedReferenceRepository(localCacheDir, referenceRepobaseUri, getFile);

        Resource rsrc = repo.get(Gavtc.of("org.foo:bar:1.2.3"));

        Assertions.assertThat(rsrc.string()).isEqualTo("http://localhost:8080/fake/org/foo/bar/1.2.3/bar-1.2.3.jar");

    }
}
