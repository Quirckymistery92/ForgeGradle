package net.minecraftforge.gradle.mcp.function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.apache.commons.io.output.NullOutputStream;
import org.gradle.api.tasks.JavaExec;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ExecuteFunction implements MCPFunction {

    private static final Pattern REPLACE_PATTERN = Pattern.compile("^\\{(\\w+)\\}$");

    protected final CompletableFuture<File> jar;
    protected final String[] jvmArgs;
    protected final String[] runArgs;
    protected final Map<String, String> envVars;

    private JsonObject data;

    public ExecuteFunction(CompletableFuture<File> jar, String[] jvmArgs, String[] runArgs, Map<String, String> envVars) {
        this.jar = jar;
        this.jvmArgs = jvmArgs;
        this.runArgs = runArgs;
        this.envVars = envVars;
    }

    @Override
    public void loadData(JsonObject data) {
        this.data = data;
    }

    @Override
    public void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        analyzeAndExtract(environment, zip, jvmArgs);
        analyzeAndExtract(environment, zip, runArgs);
    }

    @Override
    public File execute(MCPEnvironment environment) throws IOException, InterruptedException, ExecutionException {
        // Add an output and log argument if there wasn't one
        Map<String, Object> arguments = environment.getArguments();
        String outputExtension = (String)arguments.getOrDefault("outputExtension", "jar");
        arguments.computeIfAbsent("output", k -> environment.getFile("output." + outputExtension));
        arguments.computeIfAbsent("log", k -> environment.getFile("log.log"));

        // Get input and output files
        File jar = this.jar.get();
        File output = (File)environment.getArguments().get("output");

        // Find out what the inputs are
        Map<String, Object> replacedArgs = new HashMap<>();
        List<String> jvmArgList = applyVariableSubstitutions(environment, Arrays.asList(jvmArgs), arguments, replacedArgs);
        List<String> runArgList = applyVariableSubstitutions(environment, Arrays.asList(runArgs), arguments, replacedArgs);

        replacedArgs.remove("output");
        replacedArgs.remove("log");

        HashStore hashStore = new HashStore(environment.project).load(environment.getFile("lastinput.sha1"));
        hashStore.add("args", String.join(" ", runArgs));
        hashStore.add("jvmargs", String.join(" ", runArgs));
        hashStore.add("jar", jar);
        replacedArgs.forEach((key, value) -> {
            if (value instanceof File) {
                hashStore.add(key, (File)value);
            } else if (value instanceof String) {
                hashStore.add(key, (String)value);
            }
        });
        addInputs(hashStore);
        if (hashStore.isSame() && output.exists()) return output;

        // Delete previous output
        if (output.exists()) output.delete();

        // Set up working directory
        File workingDir = environment.getWorkingDir();
        workingDir.mkdirs();

        // Locate main class in jar file
        JarFile jarFile = new JarFile(jar);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        jarFile.close();

        // Execute command
        JavaExec java = environment.project.getTasks().create("_", JavaExec.class);
        java.setJvmArgs(jvmArgList);
        java.setArgs(runArgList);
        java.setClasspath(environment.project.files(jar));
        java.setWorkingDir(workingDir);
        java.setMain(mainClass);
        java.setStandardOutput(new NullOutputStream());
        java.exec();
        environment.project.getTasks().remove(java);

        // Return the output file
        hashStore.save();
        return output;
    }

    private List<String> applyVariableSubstitutions(MCPEnvironment environment, List<String> list, Map<String, Object> arguments, Map<String, Object> inputs) {
        return list.stream().map(s -> applyVariableSubstitutions(environment, s, arguments, inputs)).collect(Collectors.toList());
    }

    private String applyVariableSubstitutions(MCPEnvironment environment, String value, Map<String, Object> arguments, Map<String, Object> inputs) {
        Matcher matcher = REPLACE_PATTERN.matcher(value);
        if (!matcher.find()) return value; // Not a replaceable string

        String argName = matcher.group(1);
        if (argName != null) {
            Object argument = arguments.get(argName);
            if (argument instanceof File) {
                inputs.put(argName, argument);
                return ((File)argument).getAbsolutePath();
            } else if (argument instanceof String) {
                inputs.put(argName, argument);
                return (String)argument;
            }

            JsonElement dataElement = data.get(argName);
            if (dataElement != null) {
                inputs.put(argName, environment.getFile(dataElement.getAsString()));
                return dataElement.getAsString();
            }
        }
        throw new IllegalStateException("The string '" + value + "' did not return a valid substitution match!");
    }

    private void analyzeAndExtract(MCPEnvironment environment, ZipFile zip, String[] args) throws IOException {
        for (String arg : args) {
            Matcher matcher = REPLACE_PATTERN.matcher(arg);
            if (!matcher.find()) continue;

            String argName = matcher.group(1);
            if (argName == null) continue;

            JsonElement dataElement = data.get(argName);
            if (dataElement == null) continue;
            String referencedData = dataElement.getAsString();

            ZipEntry entry = zip.getEntry(referencedData);
            if (entry == null) continue;
            String entryName = entry.getName();

            if (entry.isDirectory()) {
                Utils.extractDirectory(environment::getFile, zip, entryName);
            } else {
                Utils.extractFile(zip, entry, environment.getFile(entryName));
            }
        }
    }

    protected void addInputs(HashStore cache) {

    }

}
