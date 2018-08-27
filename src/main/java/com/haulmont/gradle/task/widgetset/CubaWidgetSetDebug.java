/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.gradle.task.widgetset;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CubaWidgetSetDebug extends AbstractCubaWidgetSetTask {

    protected String widgetSetsDir;
    protected String widgetSetClass;
    protected Map<String, Object> compilerArgs;
    protected boolean printCompilerClassPath = false;

    protected String logLevel = "INFO";

    protected String xmx = "-Xmx768m";
    protected String xss = "-Xss8m";
    @Deprecated
    protected String xxMPS = "-XX:MaxPermSize=256m";

    public CubaWidgetSetDebug() {
        setDescription("Debug GWT widgetset");
        setGroup("Web resources");
        // set default task dependsOn
        dependsOn(getProject().getTasks().getByPath(JavaPlugin.CLASSES_TASK_NAME));
    }

    @TaskAction
    public void buildWidgetSet() {
        if (widgetSetClass == null || widgetSetClass.isEmpty()) {
            throw new IllegalStateException("Please specify \"String widgetSetClass\" for debug widgetset");
        }

        if (widgetSetsDir == null || widgetSetsDir.isEmpty()) {
            widgetSetsDir = getDefaultBuildDir();
        }

        File widgetSetsDirectory = new File(widgetSetsDir);
        if (widgetSetsDirectory.exists()) {
            FileUtils.deleteQuietly(widgetSetsDirectory);
        }
        widgetSetsDirectory.mkdir();

        List<File> compilerClassPath = collectClassPathEntries();
        List<String> gwtCompilerArgs = collectCompilerArgs();
        List<String> gwtCompilerJvmArgs = collectCompilerJvmArgs();

        getProject().javaexec(javaExecSpec -> {
            javaExecSpec.setMain("com.google.gwt.dev.codeserver.CodeServer");
            javaExecSpec.setClasspath(getProject().files(compilerClassPath));
            javaExecSpec.setArgs(gwtCompilerArgs);
            javaExecSpec.setJvmArgs(gwtCompilerJvmArgs);
        });
    }

    @InputFiles
    @SkipWhenEmpty
    FileCollection getSourceFiles() {
        getProject().getLogger().info("Analyze source projects for widgetset building in %s", getProject().getName());

        List<File> sources = new ArrayList<>();
        List<File> files = new ArrayList<>();

        SourceSet mainSourceSet = getSourceSet(getProject(), "main");

        sources.addAll(mainSourceSet.getJava().getSrcDirs());
        sources.addAll(getClassesDirs(mainSourceSet));
        sources.add(mainSourceSet.getOutput().getResourcesDir());

        for (Project dependencyProject : collectProjectsWithDependency("vaadin-client")) {
            getProject().getLogger().info("\tFound source project %s for widgetset building", dependencyProject.getName());

            SourceSet depMainSourceSet = getSourceSet(dependencyProject, "main");

            sources.addAll(depMainSourceSet.getJava().getSrcDirs());
            sources.addAll(getClassesDirs(depMainSourceSet));
            sources.add(depMainSourceSet.getOutput().getResourcesDir());
        }

        sources.forEach(sourceDir -> {
            if (sourceDir.exists()) {
                getProject()
                        .fileTree(sourceDir, f ->
                                f.setExcludes(Collections.singleton("**/.*")))
                        .forEach(files::add);
            }
        });

        return getProject().files(files);
    }

    @OutputDirectory
    public File getOutputDirectory() {
        if (widgetSetsDir == null || widgetSetsDir.isEmpty()) {
            return new File(getDefaultBuildDir());
        }
        return new File(widgetSetsDir);
    }

    protected String getDefaultBuildDir() {
        return getProject().getBuildDir().toString() + "/web-debug/VAADIN/widgetsets";
    }

    protected List<File> collectClassPathEntries() {
        List<File> compilerClassPath = new ArrayList<>();

        // import runtime dependencies such as servlet-api
        Configuration runtimeConfiguration = getProject().getConfigurations().findByName("runtime");
        if (runtimeConfiguration != null) {
            for (ResolvedArtifact artifact : runtimeConfiguration.getResolvedConfiguration().getResolvedArtifacts()) {
                compilerClassPath.add(artifact.getFile());
            }
        }

        Configuration compileConfiguration = getProject().getConfigurations().findByName("compile");
        if (compileConfiguration != null) {
            for (Project dependencyProject : collectProjectsWithDependency("vaadin-shared")) {
                SourceSet dependencyMainSourceSet = getSourceSet(dependencyProject, "main");

                compilerClassPath.addAll(dependencyMainSourceSet.getJava().getSrcDirs());
                compilerClassPath.addAll(getClassesDirs(dependencyMainSourceSet));
                compilerClassPath.add(dependencyMainSourceSet.getOutput().getResourcesDir());

                getProject().getLogger().debug(">> Widget set building Module: %s", dependencyProject.getName());
            }
        }

        SourceSet mainSourceSet = getSourceSet(getProject(), "main");

        compilerClassPath.addAll(mainSourceSet.getJava().getSrcDirs());
        compilerClassPath.addAll(getClassesDirs(mainSourceSet));
        compilerClassPath.add(mainSourceSet.getOutput().getResourcesDir());

        List<File> compileClassPathArtifacts = StreamSupport
                .stream(mainSourceSet.getCompileClasspath().spliterator(), false)
                .filter(f -> includedArtifact(f.getName()) && !compilerClassPath.contains(f))
                .collect(Collectors.toList());
        compilerClassPath.addAll(compileClassPathArtifacts);

        if (getProject().getLogger().isEnabled(LogLevel.DEBUG)) {
            StringBuilder sb = new StringBuilder();
            for (File classPathEntry : compilerClassPath) {
                sb.append('\t')
                        .append(String.valueOf(classPathEntry))
                        .append('\n');
            }
            getProject().getLogger().debug("GWT Compiler ClassPath: \n%s", sb.toString());
            getProject().getLogger().debug("");
        } else if (printCompilerClassPath) {
            StringBuilder sb = new StringBuilder();
            for (File classPathEntry : compilerClassPath) {
                sb.append('\t')
                        .append(String.valueOf(classPathEntry))
                        .append('\n');
            }

            System.out.println("GWT Compiler ClassPath: \n" + sb.toString());
            System.out.println();
        }

        return compilerClassPath;
    }

    protected List<String> collectCompilerArgs() {
        List<String> args = new ArrayList<>();

        args.addAll(Arrays.asList("-logLevel", logLevel));
        args.addAll(Arrays.asList("-workDir", getProject().file(widgetSetsDir).getAbsolutePath()));

        for (File srcDir : getSourceSet(getProject(), "main").getJava().getSrcDirs()) {
            if (srcDir.exists()) {
                args.addAll(Arrays.asList("-src", srcDir.getAbsolutePath()));
            }
        }

        for (Project dependencyProject : collectProjectsWithDependency("vaadin-client")) {
            for (File srcDir : getSourceSet(dependencyProject, "main").getJava().getSrcDirs()) {
                if (srcDir.exists()) {
                    args.addAll(Arrays.asList("-src", srcDir.getAbsolutePath()));
                }
            }
        }

        // support overriding of default parameters
        Map<String, Object> gwtCompilerArgs = new HashMap<>();
        gwtCompilerArgs.put("-XmethodNameDisplayMode", "FULL");
        if (compilerArgs != null) {
            gwtCompilerArgs.putAll(compilerArgs);
        }

        for (Map.Entry<String, Object> entry : gwtCompilerArgs.entrySet()) {
            args.add(entry.getKey());
            args.add((String) entry.getValue());
        }

        args.add(widgetSetClass);

        if (getProject().getLogger().isInfoEnabled()) {
            System.out.println("GWT Compiler args: ");
            System.out.println('\t');
            System.out.println(args);
        }

        return args;
    }

    protected List<String> collectCompilerJvmArgs() {
        compilerJvmArgs.add(xmx);
        compilerJvmArgs.add(xss);
        compilerJvmArgs.add(xxMPS);

        if (getProject().getLogger().isInfoEnabled()) {
            System.out.println("JVM Args:");
            System.out.println('\t');
            System.out.println(compilerJvmArgs);
        }

        return new LinkedList<>(compilerJvmArgs);
    }

    public void setWidgetSetsDir(String widgetSetsDir) {
        this.widgetSetsDir = widgetSetsDir;
    }

    public void setWidgetSetClass(String widgetSetClass) {
        this.widgetSetClass = widgetSetClass;
    }

    public void setCompilerArgs(Map<String, Object> compilerArgs) {
        this.compilerArgs = compilerArgs;
    }

    public void setPrintCompilerClassPath(boolean printCompilerClassPath) {
        this.printCompilerClassPath = printCompilerClassPath;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public void setXmx(String xmx) {
        this.xmx = xmx;
    }

    public void setXss(String xss) {
        this.xss = xss;
    }

    @Deprecated
    public void setXxMPS(String xxMPS) {
        this.xxMPS = xxMPS;
    }
}
