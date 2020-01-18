/*
 * Copyright © 2018 Thomas Broyer
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
package net.ltgt.gradle.apt;

import java.util.List;
import java.util.function.Function;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.process.CommandLineArgumentProvider;

class AptPlugin52 extends AptPlugin.Impl {

  @Override
  protected <T extends Task> Object createTask(
      Project project, String taskName, Class<T> taskClass, Action<T> configure) {
    return project.getTasks().register(taskName, taskClass, configure);
  }

  @Override
  protected <T extends Task> void configureTasks(
      Project project, Class<T> taskClass, Action<T> configure) {
    project.getTasks().withType(taskClass).configureEach(configure);
  }

  @Override
  protected <T extends Task> TaskProvider<T> configureTask(
      Project project, Class<T> taskClass, String taskName, Action<T> configure) {
    TaskProvider<T> task = project.getTasks().withType(taskClass).named(taskName);
    task.configure(configure);
    return task;
  }

  @Override
  protected AptPlugin.AptOptions createAptOptions() {
    return new AptOptions52();
  }

  @Override
  protected void configureCompileTask(
      AbstractCompile task, CompileOptions compileOptions, AptPlugin.AptOptions aptOptions) {
    compileOptions.getCompilerArgumentProviders().add((CommandLineArgumentProvider) aptOptions);
  }

  @Override
  protected void ensureConfigurations(Project project, SourceSet sourceSet) {
    // no-op
  }

  @Override
  protected void configureCompileTaskForSourceSet(
      Project project,
      final SourceSet sourceSet,
      SourceDirectorySet sourceDirectorySet,
      CompileOptions compileOptions) {
    // no-op
  }

  @Override
  String getAnnotationProcessorConfigurationName(SourceSet sourceSet) {
    return sourceSet.getAnnotationProcessorConfigurationName();
  }

  @Override
  void setupGeneratedSourcesDirs(Project project, SourceSetOutput sourceSetOutput) {
    // no-op
  }

  @Override
  <T extends AbstractCompile> void addSourceSetOutputGeneratedSourcesDir(
      Project project,
      SourceSetOutput sourceSetOutput,
      String compileTaskName,
      Class<T> compileTaskClass,
      Function<T, CompileOptions> getCompileOptions,
      Object taskOrProvider) {
    // no-op
  }

  @Override
  FileCollection getGeneratedSourcesDirs(SourceSetOutput sourceSetOutput) {
    return sourceSetOutput.getGeneratedSourcesDirs();
  }

  private static class AptOptions52 extends AptPlugin.AptOptions
      implements CommandLineArgumentProvider, Named {

    @Input
    @Override
    public String getName() {
      return "apt";
    }

    @Override
    public List<String> asArguments() {
      return super.asArguments();
    }
  }
}
