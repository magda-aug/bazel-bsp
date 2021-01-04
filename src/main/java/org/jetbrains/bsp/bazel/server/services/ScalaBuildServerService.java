package org.jetbrains.bsp.bazel.server.services;

import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.ScalaMainClass;
import ch.epfl.scala.bsp4j.ScalaMainClassesItem;
import ch.epfl.scala.bsp4j.ScalaMainClassesParams;
import ch.epfl.scala.bsp4j.ScalaMainClassesResult;
import ch.epfl.scala.bsp4j.ScalaTestClassesParams;
import ch.epfl.scala.bsp4j.ScalaTestClassesResult;
import ch.epfl.scala.bsp4j.ScalacOptionsItem;
import ch.epfl.scala.bsp4j.ScalacOptionsParams;
import ch.epfl.scala.bsp4j.ScalacOptionsResult;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.jetbrains.bsp.bazel.commons.Constants;
import org.jetbrains.bsp.bazel.commons.Uri;
import org.jetbrains.bsp.bazel.server.bazel.data.BazelData;
import org.jetbrains.bsp.bazel.server.resolvers.ActionGraphResolver;
import org.jetbrains.bsp.bazel.server.resolvers.TargetsResolver;
import org.jetbrains.bsp.bazel.server.utils.ActionGraphParser;

public class ScalaBuildServerService {

  private final BazelData bazelData;
  private final TargetsResolver targetsResolver;
  private final ActionGraphResolver actionGraphResolver;

  public ScalaBuildServerService(
      BazelData bazelData,
      TargetsResolver targetsResolver,
      ActionGraphResolver actionGraphResolver) {
    this.bazelData = bazelData;
    this.targetsResolver = targetsResolver;
    this.actionGraphResolver = actionGraphResolver;
  }

  public Either<ResponseError, ScalacOptionsResult> buildTargetScalacOptions(
      ScalacOptionsParams scalacOptionsParams) {
    List<String> targets =
        scalacOptionsParams.getTargets().stream()
            .map(BuildTargetIdentifier::getUri)
            .collect(Collectors.toList());

    Map<String, List<String>> targetsOptions =
        targetsResolver.getTargetsOptions(targets, "scalacopts");
    ActionGraphParser actionGraphParser =
        actionGraphResolver.getActionGraphParser(
            targets, ImmutableList.of(Constants.SCALAC, Constants.JAVAC));

    ScalacOptionsResult result =
        new ScalacOptionsResult(
            targets.stream()
                .flatMap(
                    target ->
                        collectScalacOptionsResult(
                            actionGraphParser,
                            targetsOptions.getOrDefault(target, new ArrayList<>()),
                            actionGraphParser.getInputsAsUri(target, bazelData.getExecRoot()),
                            target))
                .collect(Collectors.toList()));
    return Either.forRight(result);
  }

  public CompletableFuture<ScalaTestClassesResult> buildTargetScalaTestClasses(
      ScalaTestClassesParams scalaTestClassesParams) {
    System.out.printf("DWH: Got buildTargetScalaTestClasses: %s%n", scalaTestClassesParams);
    // TODO(illicitonion): Populate
    return CompletableFuture.completedFuture(new ScalaTestClassesResult(new ArrayList<>()));
  }

  public Either<ResponseError, ScalaMainClassesResult> buildTargetScalaMainClasses(
      ScalaMainClassesParams scalaMainClassesParams) {
    List<BuildTargetIdentifier> targets = scalaMainClassesParams.getTargets();
    String targetsUnion = Joiner.on(" + ").join(getTargetUris(targets));
    Map<String, List<String>> targetsOptions =
        targetsResolver.getTargetsOptions(targetsUnion, "scalacopts");
    Map<String, List<String>> mainClasses = targetsResolver.getTargetsMainClasses(targetsUnion);

    ScalaMainClassesResult result =
        new ScalaMainClassesResult(
            targets.stream()
                .map(
                    target ->
                        new ScalaMainClassesItem(
                            target, collectMainClasses(target, mainClasses, targetsOptions)))
                .collect(Collectors.toList()));
    return Either.forRight(result);
  }

  private List<String> getTargetUris(List<BuildTargetIdentifier> targets) {
    return targets.stream().map(BuildTargetIdentifier::getUri).collect(Collectors.toList());
  }

  private Stream<ScalacOptionsItem> collectScalacOptionsResult(
      ActionGraphParser actionGraphParser,
      List<String> options,
      List<String> inputs,
      String target) {
    List<String> suffixes = ImmutableList.of(".jar", ".js");
    return actionGraphParser.getOutputs(target, suffixes).stream()
        .map(
            output ->
                new ScalacOptionsItem(
                    new BuildTargetIdentifier(target),
                    options,
                    inputs,
                    Uri.fromExecPath(Constants.EXEC_ROOT_PREFIX + output, bazelData.getExecRoot())
                        .toString()));
  }

  private List<ScalaMainClass> collectMainClasses(
      BuildTargetIdentifier target,
      Map<String, List<String>> mainClassesNames,
      Map<String, List<String>> targetsOptions) {
    return mainClassesNames.getOrDefault(target.getUri(), new ArrayList<>()).stream()
        .map(
            mainClassName ->
                new ScalaMainClass(
                    mainClassName,
                    new ArrayList<>(),
                    targetsOptions.getOrDefault(target.getUri(), new ArrayList<>())))
        .collect(Collectors.toList());
  }
}