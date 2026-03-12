package com.eclipse.eslint.builder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.google.gson.Gson;

public class ESLintBuilder extends IncrementalProjectBuilder {

    public static final String BUILDER_ID = "com.eclipse.eslint.builder.builder";
    public static final String MARKER_TYPE = "com.eclipse.eslint.builder.problem";

    private final Gson gson = new Gson();

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        IProject project = getProject();

        if (project == null || !project.isAccessible()) {
            return null;
        }

        clearMarkers(project);
        runEslint(project);

        return null;
    }

    private void clearMarkers(IProject project) throws CoreException {
        project.deleteMarkers(MARKER_TYPE, true, IProject.DEPTH_INFINITE);
    }

    private void runEslint(IProject project) {
        try {
            // ProcessBuilder pb = new ProcessBuilder("npx", "eslint", ".", "--ext", ".js,.mjs,.cjs", "-f", "json");
            ProcessBuilder pb = new ProcessBuilder("npm", "run", "--silent", "lint", "--", "-f", "json");

            pb.directory(project.getLocation().toFile());

            Process process = pb.start();

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            int exitCode = process.waitFor();

            if (stdout == null || stdout.trim().isEmpty()) {
                if (stderr != null && !stderr.trim().isEmpty()) {
                    System.err.println("ESLint stderr: " + stderr);
                }
                return;
            }

            ESLintFileResult[] results;
            try {
                results = gson.fromJson(stdout, ESLintFileResult[].class);
            } catch (Exception e) {
                System.err.println("Invalid ESLint JSON output:");
                System.err.println(stdout);
                if (stderr != null && !stderr.trim().isEmpty()) {
                    System.err.println(stderr);
                }
                return;
            }

            if (results == null) {
                return;
            }

            for (ESLintFileResult result : results) {
                if (result == null || result.filePath == null || result.messages == null) {
                    continue;
                }

                IFile file = toWorkspaceFile(result.filePath);
                if (file == null || !file.exists()) {
                    continue;
                }

                for (ESLintMessage message : result.messages) {
                    if (message == null) {
                        continue;
                    }
                    createMarker(file, message);
                }
            }

            if (exitCode != 0 && (stderr != null && !stderr.trim().isEmpty())) {
                System.err.println("ESLint stderr: " + stderr);
            }

        } catch (Exception e) {
            e.printStackTrace();
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
            // Si falla el cálculo del offset, al menos queda el marker en la línea correcta.
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
}
