package net.minecraftforge.gradle.patcher.task;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.gradle.common.util.ManifestJson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public class DownloadMCMetaTask extends DefaultTask {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final Gson GSON = new GsonBuilder().create();

    private String mcVersion;
    private File manifest = getProject().file("build/" + getName() + "/manifest.json");
    private File output = getProject().file("build/" + getName() + "/version.json");

    @TaskAction
    public void downloadMCMeta() throws IOException {
        try (InputStream manin = new URL(MANIFEST_URL).openStream()) {
            URL url = GSON.fromJson(new InputStreamReader(manin), ManifestJson.class).getUrl(getMCVersion());
            if (url != null) {
                FileUtils.copyURLToFile(url, getOutput());
            } else {
                throw new RuntimeException("Missing version from manifest: " + getMCVersion());
            }
        }
    }

    @Input
    public String getMCVersion() {
        return mcVersion;
    }

    public File getManifest() {
        return manifest;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setMcVersion(String mcVersion) {
        this.mcVersion = mcVersion;
    }

    public void setManifest(File manifest) {
        this.manifest = manifest;
    }

    public void setOutput(File output) {
        this.output = output;
    }
}
