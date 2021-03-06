package net.minecraftforge.gradle.common.util;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.internal.hash.HashUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class HashStore {

    private final String root;
    private final Map<String, String> oldHashes = new HashMap<>();
    private final Map<String, String> newHashes = new HashMap<>();
    private File target;

    public HashStore() {
        this.root = "";
    }
    public HashStore(Project project) {
        this.root = project.getRootDir().getAbsolutePath();
    }
    public HashStore(File root) {
        this.root = root.getAbsolutePath();
    }

    public boolean areSame(File... files) {
        for(File file : files) {
            if(!isSame(file)) return false;
        }
        return true;
    }

    public boolean areSame(Iterable<File> files) {
        for(File file : files) {
            if(!isSame(file)) return false;
        }
        return true;
    }

    public boolean isSame(File file) {
        try {
            String path = getPath(file);
            String hash = oldHashes.get(path);
            if (hash == null) {
                if (file.exists()) {
                    newHashes.put(path, HashFunction.SHA1.hash(file));
                    return false;
                }
                return true;
            }
            HashUtil.sha1(file);
            String fileHash = HashFunction.SHA1.hash(file);
            newHashes.put(path, fileHash);
            return fileHash.equals(hash);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public HashStore load(File file) throws IOException {
        this.target = file;
        oldHashes.clear();
        if(!file.exists()) return this;
        for (String line : FileUtils.readLines(file)) {
            String[] split = line.split("=");
            oldHashes.put(split[0], split[1]);
        }
        return this;
    }

    public HashStore add(String key, String data) {
        newHashes.put(key, HashFunction.SHA1.hash(data));
        return this;
    }

    public HashStore add(String key, byte[] data) {
        newHashes.put(key, HashFunction.SHA1.hash(data));
        return this;
    }

    public HashStore add(String key, File file) {
        try {
            newHashes.put(key == null ? getPath(file) : key, HashFunction.SHA1.hash(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public HashStore add(File... files) {
        for (File file : files) {
            add(null, file);
        }
        return this;
    }
    public HashStore add(Iterable<File> files) {
        for (File file : files) {
            add(null, file);
        }
        return this;
    }
    public HashStore add(File file) {
        add(null, file);
        return this;
    }

    public boolean isSame() {
        return oldHashes.equals(newHashes);
    }

    public void save() throws IOException {
        if (target == null) {
            throw new RuntimeException("HashStore.save() called without load(File) so we dont know where to save it! Use load(File) or save(File)");
        }
        save(target);
    }
    public void save(File file) throws IOException {
        FileUtils.writeByteArrayToFile(file, newHashes.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n")).getBytes());
    }

    private String getPath(File file) {
        String path = file.getAbsolutePath();
        if (path.startsWith(root)) {
            return path.substring(root.length()).replace('\\', '/');
        } else {
            return path.replace('\\', '/');
        }
    }

}
