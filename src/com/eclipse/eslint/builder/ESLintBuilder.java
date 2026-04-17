package com.eclipse.eslint.builder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.osgi.framework.FrameworkUtil;

import com.google.gson.Gson;

public class ESLintBuilder extends IncrementalProjectBuilder {

    public static final String BUILDER_ID = "com.eclipse.eslint.builder.builder";
    public static final String MARKER_TYPE = "com.eclipse.eslint.builder.problem";

    private static final String PLUGIN_ID = "com.eclipse.eslint.builder";
    private static final ILog LOG = Platform.getLog(FrameworkUtil.getBundle(ESLintBuilder.class));

    private static final long DEBOUNCE_MS = 500L;

    /**
     * Si un proyecto ya tiene un lint pendiente, no encolamos otro.
     */
    private static final Map<String, Job> PENDING_PROJECTS = new ConcurrentHashMap<String, Job>();

    private final Gson gson = new Gson();

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        IProject project = getProject();

        if (project == null || !project.isAccessible()) {
            logInfo("build skipped: project null or not accessible");
            return null;
        }

        String projectName = project.getName();
        logInfo("build triggered: kind=" + kind + ", project=" + projectName);

        Job job = new Job("ESLint lint " + projectName) {
            @Override
            protected IStatus run(IProgressMonitor jobMonitor) {
                try {
                    logInfo("lint job start: project=" + projectName);
                    clearMarkers(project);
                    runEslint(project);
                    logInfo("lint job end: project=" + projectName);
                    return Status.OK_STATUS;
                } catch (CoreException e) {
                    logError("lint job failed: project=" + projectName, e);
                    return new Status(IStatus.ERROR, PLUGIN_ID, "lint job failed: " + projectName, e);
                } finally {
                    PENDING_PROJECTS.remove(projectName, this);
                    logInfo("lint pending cleared: project=" + projectName);
                }
            }
        };
        Job lastJob = PENDING_PROJECTS.put(projectName, job);
        if (lastJob != null) {
            logInfo("build rescheduled: canceled previous pending job for project=" + projectName);
            lastJob.cancel();
        }
        job.setSystem(true);
        job.schedule(DEBOUNCE_MS);

        return null;
    }

    private void clearMarkers(IProject project) throws CoreException {
        logInfo("clearMarkers start: project=" + project.getName());
        project.deleteMarkers(MARKER_TYPE, true, IProject.DEPTH_INFINITE);
        logInfo("clearMarkers end: project=" + project.getName());
    }

    private void runEslint(IProject project) {
        try {
            logInfo("runEslint start: project=" + project.getName());
            java.io.File projectDir = project.getLocation().toFile();
            String npmCommand = resolveNpmCommand(projectDir);
            String projectLoc = project.getLocation().toOSString();

            logInfo("using npm command: " + npmCommand);

            ProcessBuilder pb = new ProcessBuilder(npmCommand, "--prefix", projectLoc, "run", "lint", "--silent", "--",
                    "-f", "json");
            if (!"npm".equals(npmCommand)) {
                String binDir = new java.io.File(npmCommand).getParent();
                String oldPath = pb.environment().get("PATH");
                pb.environment().put("PATH", binDir + java.io.File.pathSeparator + (oldPath != null ? oldPath : ""));
            }

            pb.directory(projectDir);

            logInfo("starting process: npm --prefix " + projectLoc + " run --silent lint -- -f json");

            Process process = pb.start();

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            int exitCode = process.waitFor();
            logInfo("eslint process finished: project=" + project.getName() + ", exitCode=" + exitCode);

            if (stdout == null || stdout.trim().isEmpty()) {
                logInfo("eslint stdout empty: project=" + project.getName());
                if (stderr != null && !stderr.trim().isEmpty()) {
                    logError("ESLint stderr: project=" + project.getName() + "\n" + stderr, null);
                }
                return;
            }

            ESLintFileResult[] results;
            try {
                results = gson.fromJson(stdout, ESLintFileResult[].class);
                logInfo("eslint json parsed: project=" + project.getName() + ", results="
                        + (results != null ? results.length : 0));
            } catch (Exception e) {
                logError("Invalid ESLint JSON output: project=" + project.getName() + "\n" + stdout, e);
                if (stderr != null && !stderr.trim().isEmpty()) {
                    logError("ESLint stderr: project=" + project.getName() + "\n" + stderr, null);
                }
                return;
            }

            if (results == null) {
                logInfo("eslint results null: project=" + project.getName());
                return;
            }

            int markerCount = 0;

            for (ESLintFileResult result : results) {
                if (result == null || result.filePath == null || result.messages == null) {
                    continue;
                }

                IFile file = toWorkspaceFile(result.filePath);
                if (file == null || !file.exists()) {
                    logInfo("workspace file not found for result: " + result.filePath);
                    continue;
                }

                for (ESLintMessage message : result.messages) {
                    if (message == null) {
                        continue;
                    }
                    createMarker(file, message);
                    markerCount++;
                }
            }

            logInfo("markers created: project=" + project.getName() + ", count=" + markerCount);

            if (exitCode != 0 && (stderr != null && !stderr.trim().isEmpty())) {
                logError("ESLint stderr: project=" + project.getName() + "\n" + stderr, null);
            }

        } catch (Exception e) {
            logError("runEslint failed: project=" + project.getName(), e);
        }
    }

    private String readStream(InputStream inputStream) throws Exception {
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        return output.toString();
    }

    private IFile toWorkspaceFile(String absoluteFilePath) {
        IPath path = Path.fromOSString(absoluteFilePath);
        return ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(path);
    }

    private void createMarker(IFile file, ESLintMessage message) throws CoreException {
        IMarker marker = file.createMarker(MARKER_TYPE);

        marker.setAttribute(IMarker.MESSAGE, message.message != null ? message.message : "ESLint error");
        marker.setAttribute(IMarker.LINE_NUMBER, Math.max(message.line, 1));
        marker.setAttribute(IMarker.SEVERITY, toMarkerSeverity(message.severity));

        try (java.io.InputStream input = file.getContents()) {
            String content = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            int line = Math.max(message.line, 1);
            int column = Math.max(message.column, 1);

            int charStart = getCharStart(content, line, column);
            int charEnd = Math.min(charStart + 1, content.length());

            marker.setAttribute(IMarker.CHAR_START, charStart);
            marker.setAttribute(IMarker.CHAR_END, charEnd);
        } catch (Exception e) {
            logError("createMarker offset failed: file=" + file.getFullPath(), e);
        }
    }

    private int getCharStart(String content, int targetLine, int targetColumn) {
        int currentLine = 1;
        int index = 0;
        int length = content.length();

        while (index < length && currentLine < targetLine) {
            char c = content.charAt(index++);
            if (c == '\n') {
                currentLine++;
            }
        }

        return Math.min(index + targetColumn - 1, length);
    }

    private int toMarkerSeverity(int eslintSeverity) {
        if (eslintSeverity >= 2) {
            return IMarker.SEVERITY_ERROR;
        }
        return IMarker.SEVERITY_WARNING;
    }

    private void logInfo(String message) {
        System.out.println("[ESLintBuilder] " + message);
        LOG.log(new Status(IStatus.INFO, PLUGIN_ID, message));
    }

    private void logError(String message, Throwable e) {
        System.err.println("[ESLintBuilder] " + message);
        if (e != null) {
            e.printStackTrace();
            LOG.log(new Status(IStatus.ERROR, PLUGIN_ID, message, e));
        } else {
            LOG.log(new Status(IStatus.ERROR, PLUGIN_ID, message));
        }
    }

    private String resolveNpmCommand(java.io.File projectDir) {
        String local = resolveLocalProjectNpm(projectDir);
        if (local != null) {
            logInfo("npm resolved from local project config: " + local);
            return local;
        }

        String personal = resolvePersonalNpm();
        if (personal != null) {
            logInfo("npm resolved from active environment: " + personal);
            return personal;
        }

        logInfo("npm resolved from fallback command: npm");
        return "npm";
    }

    private String resolveLocalProjectNpm(java.io.File projectDir) {
        try {
            String version = readFirstLineIfExists(new java.io.File(projectDir, ".nvmrc"));
            if (version == null) {
                version = readFirstLineIfExists(new java.io.File(projectDir, ".node-version"));
            }
            if (version == null) {
                version = readNodeVersionFromPackageJson(new java.io.File(projectDir, "package.json"));
            }

            if (version == null || version.trim().isEmpty()) {
                return null;
            }

            version = normalizeNodeVersion(version);

            if (version == null) {
                return null;
            }

            String userHome = System.getenv("HOME");
            String home = System.getProperty("user.home");

            String[] nvmDirs = new String[] { new java.io.File(projectDir, ".nvm").getAbsolutePath(),
                    System.getenv("NVM_DIR"), home != null ? home + "/.nvm" : null,
                    userHome != null ? userHome + "/.nvm" : null };

            for (String nvmDir : nvmDirs) {
                if (nvmDir == null || nvmDir.trim().isEmpty()) {
                    continue;
                }

                java.io.File npm = new java.io.File(nvmDir, "versions/node/" + version + "/bin/npm");
                if (npm.isFile() && npm.canExecute()) {
                    return npm.getAbsolutePath();
                }
            }

            return null;
        } catch (Exception e) {
            logError("resolveLocalProjectNpm failed", e);
            return null;
        }
    }

    private String resolvePersonalNpm() {
        try {
            String nvmBin = System.getenv("NVM_BIN");
            if (nvmBin != null && !nvmBin.trim().isEmpty()) {
                java.io.File npm = new java.io.File(nvmBin, "npm");
                if (npm.isFile() && npm.canExecute()) {
                    return npm.getAbsolutePath();
                }
            }

            String userHome = System.getenv("HOME");
            String home = System.getProperty("user.home");

            String[] nvmDirs = new String[] { System.getenv("NVM_DIR"), home != null ? home + "/.nvm" : null,
                    userHome != null ? userHome + "/.nvm" : null };

            for (String nvmDir : nvmDirs) {
                if (nvmDir == null || nvmDir.trim().isEmpty()) {
                    continue;
                }

                java.io.File aliasDefault = new java.io.File(nvmDir, "alias/default");
                if (aliasDefault.isFile()) {
                    try {
                        String alias = new String(java.nio.file.Files.readAllBytes(aliasDefault.toPath()),
                                java.nio.charset.StandardCharsets.UTF_8).trim();

                        if (!alias.isEmpty()) {
                            java.io.File npm = new java.io.File(nvmDir, "versions/node/v" + alias + "/bin/npm");
                            if (npm.isFile() && npm.canExecute()) {
                                return npm.getAbsolutePath();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                java.io.File versionsDir = new java.io.File(nvmDir, "versions/node");
                java.io.File[] versionDirs = versionsDir.listFiles(java.io.File::isDirectory);

                if (versionDirs == null) {
                    continue;
                }

                for (java.io.File versionDir : versionDirs) {
                    java.io.File npm = new java.io.File(versionDir, "bin/npm");
                    if (npm.isFile() && npm.canExecute()) {
                        return npm.getAbsolutePath();
                    }
                }
            }

            String path = System.getenv("PATH");
            if (path != null && !path.trim().isEmpty()) {
                String[] entries = path.split(java.io.File.pathSeparator);
                for (String entry : entries) {
                    if (entry == null || entry.trim().isEmpty()) {
                        continue;
                    }

                    java.io.File npm = new java.io.File(entry, "npm");
                    if (npm.isFile() && npm.canExecute()) {
                        return npm.getAbsolutePath();
                    }
                }
            }

            return null;
        } catch (Exception e) {
            logError("resolvePersonalNpm failed", e);
            return null;
        }
    }

    private String readNodeVersionFromPackageJson(java.io.File file) {
        if (file == null || !file.isFile()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }

            java.util.regex.Matcher matcher = Pattern.compile("\"node\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(content.toString());

            if (!matcher.find()) {
                return null;
            }

            return matcher.group(1);
        } catch (Exception e) {
            logError("readNodeVersionFromPackageJson failed: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private String normalizeNodeVersion(String version) {
        if (version == null) {
            return null;
        }

        String normalized = version.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        normalized = normalized.replaceAll("\\s+", "");
        normalized = normalized.replaceFirst("^[^0-9v]+", "");

        if (normalized.isEmpty()) {
            return null;
        }

        if (!normalized.startsWith("v")) {
            normalized = "v" + normalized;
        }

        return normalized.matches("^v\\d+(\\.\\d+){0,2}$") ? normalized : null;
    }

    private String readFirstLineIfExists(java.io.File file) {
        if (file == null || !file.isFile()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            line = line.trim();
            return line.isEmpty() ? null : line;
        } catch (Exception e) {
            logError("readFirstLineIfExists failed: " + file.getAbsolutePath(), e);
            return null;
        }
    }
}