package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import org.l2x6.jrebuild.core.build.Resource;
import org.l2x6.pom.tuner.model.Gavtc;

public class CachedReferenceRepository {
    private final Path localCacheDir;
    private final String referenceRepobaseUri;
    private final BiConsumer<String, Path> getFile;

    public CachedReferenceRepository(Path localCacheDir, String referenceRepobaseUri, BiConsumer<String, Path> getFile) {
        super();
        if (referenceRepobaseUri.endsWith("/")) {
            throw new IllegalArgumentException("Should not end with a slash /:" + referenceRepobaseUri);
        }

        this.localCacheDir = localCacheDir;
        this.referenceRepobaseUri = referenceRepobaseUri;
        this.getFile = getFile;
    }

    public Resource get(Gavtc gavtc) {

        final String repositoryPath = gavtc.getRepositoryPath();
        Path localPath = localCacheDir.resolve(repositoryPath);
        if (Files.isRegularFile(localPath)) {
            return Resource.of(localPath);
        }
        Path localPathParent = localPath.getParent();
        try {
            Files.createDirectories(localPathParent);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + localPathParent, e);
        }

        Path lockPath = localCacheDir.resolve(repositoryPath + ".lock");
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(lockPath.toFile(), "rws");
                FileChannel channel = randomAccessFile.getChannel();
                FileLock lock = channel.lock()) {
            Path tempPath = localCacheDir.resolve(repositoryPath + ".tmp");
            getFile.accept(referenceRepobaseUri + "/" + repositoryPath, tempPath);
            Files.move(tempPath, localPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create or lock " + lockPath, e);
        }
        return Resource.of(localPath);
    }
}
