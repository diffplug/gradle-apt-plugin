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
package net.ltgt.gradle.apt

import static net.ltgt.gradle.apt.IntegrationTestHelper.TEST_GRADLE_VERSION

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.gradle.util.TextUtil
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Specification

class AptPluginIntegrationSpec extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File settingsFile
  File buildFile

  def setup() {
    settingsFile = testProjectDir.newFile('settings.gradle')
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """\
      buildscript {
        repositories {
          maven { url = uri("${TextUtil.normaliseFileSeparators(new File('build/repository').absolutePath)}") }
        }
        dependencies {
          classpath 'net.ltgt.gradle:gradle-apt-plugin:${System.getProperty('plugin.version')}'
        }
      }
    """.stripIndent()
  }

  def "simple non-java project"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'

      task javaCompilationTask(type: JavaCompile) {
        source 'src/'
        include '**/*.java'
        classpath = project.files()
        destinationDir = project.file('build/classes')
        sourceCompatibility = org.gradle.api.JavaVersion.current()
        targetCompatibility = org.gradle.api.JavaVersion.current()
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'simple'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
      package simple;

      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
            .withGradleVersion(TEST_GRADLE_VERSION)
            .withProjectDir(testProjectDir.root)
            .withArguments('javaCompilationTask')
            .build()

    then:
    result.task(':javaCompilationTask').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'build/classes/simple/HelloWorld.class').exists()
  }

  def "simple java project"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'java'
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'main', 'java', 'simple'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
      package simple;

      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('src', 'test', 'java', 'simple'), 'HelloWorldTest.java')
    f.createNewFile()
    f << """\
      package simple;

      public class HelloWorldTest {
        // Not a real unit-test
        public static void main(String[] args) {
          System.out.println(new HelloWorld().sayHello("World"));
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('compileTestJava')
        .build()

    then:
    result.task(':compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':compileTestJava').outcome == TaskOutcome.SUCCESS
  }

  def "simple java project with compile-only dependency"() {
    given:
    settingsFile << """\
      include 'annotations'
      include 'core'
    """.stripIndent()

    buildFile << """\
      subprojects {
        apply plugin: 'java'
      }
      project('core') {
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compileOnly project(':annotations')
        }
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'java', 'core'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
      package core;

      import annotations.MyAnnotation;

      @MyAnnotation
      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    expect:

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments(':core:javadoc')
        .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:javadoc').outcome == TaskOutcome.SUCCESS
  }

  def "simple java project with annotation processor"() {
    given:
    settingsFile << """\
      include 'annotations'
      include 'processor'
      include 'core'
    """.stripIndent()

    buildFile << """\
      subprojects {
        apply plugin: 'java'
      }
      project('core') {
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compileOnly project(':annotations')
          annotationProcessor project(':processor')
        }
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'java', 'processor'), 'MyAnnotationProcessor.java')
    f.createNewFile()
    f << """\
      package processor;

      import javax.annotation.processing.AbstractProcessor;
      import javax.annotation.processing.RoundEnvironment;
      import javax.annotation.processing.SupportedAnnotationTypes;
      import javax.lang.model.SourceVersion;
      import javax.lang.model.element.TypeElement;
      import javax.lang.model.util.ElementFilter;
      import javax.tools.Diagnostic;
      import javax.tools.FileObject;
      import javax.tools.StandardLocation;
      import java.io.IOException;
      import java.io.PrintWriter;
      import java.util.Set;
      import java.util.TreeSet;

      @SupportedAnnotationTypes(MyAnnotationProcessor.MY_ANNOTATION)
      public class MyAnnotationProcessor extends AbstractProcessor {

        static final String MY_ANNOTATION = "annotations.MyAnnotation";

        @Override
        public SourceVersion getSupportedSourceVersion() {
          return SourceVersion.latest();
        }

        private Set<String> annotatedElements = new TreeSet<>();

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
          for (TypeElement annotatedElement : ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(
              processingEnv.getElementUtils().getTypeElement(MY_ANNOTATION)))) {
            annotatedElements.add(annotatedElement.getQualifiedName().toString());
          }
          if (roundEnv.processingOver()) {
            try {
              FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "annotated-elements");
              try (PrintWriter w = new PrintWriter(f.openWriter())) {
                for (String annotatedElement : annotatedElements) {
                  w.println(annotatedElement);
                }
              }
            } catch (IOException e) {
              processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
            }
          }
          return false;
        }
      }
    """.stripIndent()
    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'resources', 'META-INF', 'services'), 'javax.annotation.processing.Processor')
    f.createNewFile()
    f << """\
      processor.MyAnnotationProcessor
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'java', 'core'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
      package core;

      import annotations.MyAnnotation;

      @MyAnnotation
      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    expect:

    when:
    def result = GradleRunner.create()
            .withGradleVersion(TEST_GRADLE_VERSION)
            .withProjectDir(testProjectDir.root)
            .withArguments(':core:javadoc')
            .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':processor:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:javadoc').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'core/build/classes/java/main/annotated-elements').text.trim() == "core.HelloWorld"
  }

  def "simple non-groovy project"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'

      configurations {
        groovy
      }
      dependencies {
        groovy localGroovy()
      }

      task groovyCompilationTask(type: GroovyCompile) {
        source 'src/'
        include '**/*.groovy'
        classpath = configurations.groovy
        destinationDir = project.file('build/classes')
        sourceCompatibility = org.gradle.api.JavaVersion.current()
        targetCompatibility = org.gradle.api.JavaVersion.current()
        groovyClasspath = configurations.groovy
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'simple'), 'HelloWorld.groovy')
    f.createNewFile()
    f << """\
      package simple;

      class HelloWorld {
        String sayHello(String name) {
          "Hello, \${name}!";
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
            .withGradleVersion(TEST_GRADLE_VERSION)
            .withProjectDir(testProjectDir.root)
            .withArguments('groovyCompilationTask')
            .build()

    then:
    result.task(':groovyCompilationTask').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'build/classes/simple/HelloWorld.class').exists()
  }

  def "simple groovy project"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'groovy'

      dependencies {
        compile localGroovy()
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'main', 'groovy', 'simple'), 'HelloWorld.groovy')
    f.createNewFile()
    f << """\
      package simple;

      class HelloWorld {
        String sayHello(String name) {
          "Hello, \${name}!";
        }
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('src', 'test', 'groovy', 'simple'), 'HelloWorldTest.groovy')
    f.createNewFile()
    f << """\
      package simple;

      class HelloWorldTest {
        // Not a real unit-test
        public static void main(String[] args) {
          System.out.println(new HelloWorld().sayHello("World"));
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('compileTestGroovy')
        .build()

    then:
    result.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':compileTestGroovy').outcome == TaskOutcome.SUCCESS
  }

  def "simple groovy project with compile-only dependency"() {
    given:
    settingsFile << """\
      include 'annotations'
      include 'core'
    """.stripIndent()

    buildFile << """\
      project('annotations') {
        apply plugin: 'java'
      }
      project('core') {
        apply plugin: 'groovy'
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compile localGroovy()
          compileOnly project(':annotations')
        }
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'groovy', 'core'), 'HelloWorld.groovy')
    f.createNewFile()
    f << """\
      package core;

      import annotations.MyAnnotation;

      @MyAnnotation
      class HelloWorld {
        String sayHello(String name) {
          "Hello, \${name}!";
        }
      }
    """.stripIndent()

    expect:

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments(':core:groovydoc')
        .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':core:groovydoc').outcome == TaskOutcome.SUCCESS
  }

  def "simple groovy project with annotation processor"() {
    given:
    settingsFile << """\
      include 'annotations'
      include 'processor'
      include 'core'
    """.stripIndent()

    buildFile << """\
      project('annotations') {
        apply plugin: 'java'
      }
      project('processor') {
        apply plugin: 'groovy'

        dependencies {
          compile localGroovy()
        }
      }
      project('core') {
        apply plugin: 'groovy'
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compile localGroovy()
          compileOnly project(':annotations')
          annotationProcessor project(':processor')
        }

        tasks.withType(GroovyCompile) {
          groovyOptions.javaAnnotationProcessing = true
        }
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'groovy', 'processor'), 'MyAnnotationProcessor.groovy')
    f.createNewFile()
    f << """\
      package processor

      import javax.annotation.processing.AbstractProcessor
      import javax.annotation.processing.RoundEnvironment
      import javax.annotation.processing.SupportedAnnotationTypes
      import javax.lang.model.SourceVersion
      import javax.lang.model.element.TypeElement
      import javax.lang.model.util.ElementFilter
      import javax.tools.Diagnostic
      import javax.tools.FileObject
      import javax.tools.StandardLocation

      @SupportedAnnotationTypes(MyAnnotationProcessor.MY_ANNOTATION)
      class MyAnnotationProcessor extends AbstractProcessor {

        static final String MY_ANNOTATION = "annotations.MyAnnotation"

        @Override
        SourceVersion getSupportedSourceVersion() {
          return SourceVersion.latest()
        }

        private Set<String> annotatedElements = new TreeSet<>()

        @Override
        boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
          annotatedElements.addAll(ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(
              processingEnv.getElementUtils().getTypeElement(MY_ANNOTATION))).collect {
            it.qualifiedName.toString()
          })
          if (roundEnv.processingOver()) {
            try {
              FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "annotated-elements")
              f.openWriter().withPrintWriter { w ->
                annotatedElements.each {
                  w.println(it)
                }
              }
            } catch (IOException e) {
              processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage())
            }
          }
          return false
        }
      }
    """.stripIndent()
    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'resources', 'META-INF', 'services'), 'javax.annotation.processing.Processor')
    f.createNewFile()
    f << """\
      processor.MyAnnotationProcessor
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'groovy', 'core'), 'HelloWorld.groovy')
    f.createNewFile()
    f << """\
      package core;

      import annotations.MyAnnotation;

      @MyAnnotation
      class HelloWorld {
        String sayHello(String name) {
          "Hello, \${name}!";
        }
      }
    """.stripIndent()

    expect:

    when:
    def result = GradleRunner.create()
            .withGradleVersion(TEST_GRADLE_VERSION)
            .withProjectDir(testProjectDir.root)
            .withArguments(':core:groovydoc')
            .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':processor:compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':core:groovydoc').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'core/build/classes/groovy/main/annotated-elements').text.trim() == "core.HelloWorld"
  }

  @Requires({ GradleVersion.version(TEST_GRADLE_VERSION) >= GradleVersion.version("3.5") })
  def "is build-cache friendly"() {
    given:
    settingsFile << """\
      include 'annotations'
      include 'processor'
      include 'core'
      buildCache {
        local(DirectoryBuildCache) {
          directory = new File(rootDir, 'build-cache')
        }
      }
    """.stripIndent()

    buildFile << """\
      project('annotations') {
        apply plugin: 'java'
      }
      project('processor') {
        apply plugin: 'groovy'

        dependencies {
          compile localGroovy()
        }
      }
      project('core') {
        apply plugin: 'groovy'
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compileOnly project(':annotations')
          annotationProcessor project(':processor')

          testCompile localGroovy()
          testCompileOnly project(':annotations')
          testAnnotationProcessor project(':processor')
        }

        compileJava {
          aptOptions.processorArgs = ['foo': 'bar']
        }

        compileTestGroovy {
          groovyOptions.javaAnnotationProcessing = true
        }
      }
      // With JDK 11, Gradle < 4.10 will choke on class version 55
      subprojects {
        sourceCompatibility = 8
        targetCompatibility = 8
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'Helper.java')
    f.createNewFile()
    f << """\
      package annotations;

      public @interface Helper {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'groovy', 'processor'), 'HelperProcessor.groovy')
    f.createNewFile()
    f << """\
      package processor

      import javax.annotation.processing.AbstractProcessor
      import javax.annotation.processing.RoundEnvironment
      import javax.annotation.processing.SupportedAnnotationTypes
      import javax.lang.model.SourceVersion
      import javax.lang.model.element.TypeElement
      import javax.lang.model.util.ElementFilter
      import javax.tools.Diagnostic
      import javax.tools.FileObject
      import javax.tools.StandardLocation

      @SupportedAnnotationTypes(HelperProcessor.HELPER)
      class HelperProcessor extends AbstractProcessor {

        static final String HELPER = "annotations.Helper"

        @Override
        SourceVersion getSupportedSourceVersion() {
          return SourceVersion.latest()
        }

        @Override
        boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
          ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(
              processingEnv.getElementUtils().getTypeElement(HELPER))).each { element ->
            String helperName = "\${element.getSimpleName()}Helper"
            try {
              FileObject f = processingEnv.getFiler().createSourceFile("\${element.getQualifiedName()}Helper", element)
              f.openWriter().withWriter { w ->
                w << ""\"\\
                  package \${processingEnv.getElementUtils().getPackageOf(element)};

                  class \${element.getSimpleName()}Helper {
                    static String getValue() { return "\${element.getQualifiedName()}"; }
                  }
                ""\".stripIndent()
              }
            } catch (IOException e) {
              processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage())
            }
          }
          return false
        }
      }
    """.stripIndent()
    f = new File(testProjectDir.newFolder('processor', 'src', 'main', 'resources', 'META-INF', 'services'), 'javax.annotation.processing.Processor')
    f.createNewFile()
    f << """\
      processor.HelperProcessor
    """.stripIndent()

    // Java file in (main) sources
    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'java', 'core'), 'HelloWorldJ.java')
    f.createNewFile()
    f << """\
      package core;

      import annotations.Helper;

      @Helper
      class HelloWorldJ {
        String sayHello(String name) {
          return "Hello, " + name + "! I'm " + HelloWorldJHelper.getValue() + ".";
        }
      }
    """.stripIndent()

    // Java file in Groovy (test) sources
    f = new File(testProjectDir.newFolder('core', 'src', 'test', 'groovy', 'test'), 'HelloWorldJ.java')
    f.createNewFile()
    f << """\
      package test;

      import annotations.Helper;

      @Helper
      class HelloWorldJ {
        String sayHello(String name) {
          return "Hello, " + name + "! I'm " + HelloWorldJHelper.getValue() + ".";
        }
      }
    """.stripIndent()

    // Groovy file in (test) sources
    f = new File(f.parentFile, 'HelloWorldG.groovy')
    f.createNewFile()
    f << """\
      package test;

      import annotations.Helper;

      @Helper
      class HelloWorldG {
        String sayHello(String name) {
          "Hello, \${name}! I'm \${HelloWorldGHelper.getValue()}.";
        }
      }
    """.stripIndent()

    expect:

    when:
    def result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('--build-cache', ':core:testClasses')
        .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':processor:compileJava').outcome == TaskOutcome.NO_SOURCE
    result.task(':processor:compileGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileGroovy').outcome == TaskOutcome.NO_SOURCE
    result.task(':core:compileTestJava').outcome == TaskOutcome.NO_SOURCE
    result.task(':core:compileTestGroovy').outcome == TaskOutcome.SUCCESS
    result.task(':core:classes').outcome == TaskOutcome.SUCCESS
    result.task(':core:testClasses').outcome == TaskOutcome.SUCCESS
    new File(testProjectDir.root, 'core/build/generated/sources/annotationProcessor/java/main/core/HelloWorldJHelper.java').exists()
    new File(testProjectDir.root, 'core/build/generated/sources/annotationProcessor/groovy/test/test/HelloWorldJHelper.java').exists()
    new File(testProjectDir.root, 'core/build/generated/sources/annotationProcessor/groovy/test/test/HelloWorldGHelper.java').exists()
    new File(testProjectDir.root, 'core/build/classes/java/main/core/HelloWorldJ.class').exists()
    new File(testProjectDir.root, 'core/build/classes/java/main/core/HelloWorldJHelper.class').exists()
    new File(testProjectDir.root, 'core/build/classes/groovy/test/test/HelloWorldJ.class').exists()
    new File(testProjectDir.root, 'core/build/classes/groovy/test/test/HelloWorldJHelper.class').exists()
    new File(testProjectDir.root, 'core/build/classes/groovy/test/test/HelloWorldG.class').exists()
    new File(testProjectDir.root, 'core/build/classes/groovy/test/test/HelloWorldGHelper.class').exists()

    when:
    new File(testProjectDir.root, 'annotations/build').deleteDir()
    new File(testProjectDir.root, 'processor/build').deleteDir()
    new File(testProjectDir.root, 'core/build').deleteDir()
    result = GradleRunner.create()
        .withGradleVersion(TEST_GRADLE_VERSION)
        .withProjectDir(testProjectDir.root)
        .withArguments('--build-cache', ':core:testClasses')
        .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.FROM_CACHE
    result.task(':processor:compileJava').outcome == TaskOutcome.NO_SOURCE
    result.task(':processor:compileGroovy').outcome == TaskOutcome.FROM_CACHE
    result.task(':core:compileJava').outcome == TaskOutcome.FROM_CACHE
    result.task(':core:compileGroovy').outcome == TaskOutcome.NO_SOURCE
    result.task(':core:compileTestJava').outcome == TaskOutcome.NO_SOURCE
    result.task(':core:compileTestGroovy').outcome == TaskOutcome.FROM_CACHE
    result.task(':core:classes').outcome == TaskOutcome.UP_TO_DATE
    result.task(':core:testClasses').outcome == TaskOutcome.UP_TO_DATE
    new File(testProjectDir.root, 'core/build/generated/sources/annotationProcessor/java/main/core/HelloWorldJHelper.java').exists()
    new File(testProjectDir.root, 'core/build/generated/sources/annotationProcessor/groovy/test/test/HelloWorldJHelper.java').exists()
    new File(testProjectDir.root, 'core/build/generated/sources/annotationProcessor/groovy/test/test/HelloWorldGHelper.java').exists()
    new File(testProjectDir.root, 'core/build/classes/java/main/core/HelloWorldJ.class').exists()
    new File(testProjectDir.root, 'core/build/classes/java/main/core/HelloWorldJHelper.class').exists()
    new File(testProjectDir.root, 'core/build/classes/groovy/test/test/HelloWorldJ.class').exists()
    new File(testProjectDir.root, 'core/build/classes/groovy/test/test/HelloWorldJHelper.class').exists()
    new File(testProjectDir.root, 'core/build/classes/groovy/test/test/HelloWorldG.class').exists()
    new File(testProjectDir.root, 'core/build/classes/groovy/test/test/HelloWorldGHelper.class').exists()
  }
}
