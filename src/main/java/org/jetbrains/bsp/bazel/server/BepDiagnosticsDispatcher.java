package org.jetbrains.bsp.bazel.server;

import ch.epfl.scala.bsp4j.BuildClient;
import ch.epfl.scala.bsp4j.BuildTargetIdentifier;
import ch.epfl.scala.bsp4j.Diagnostic;
import ch.epfl.scala.bsp4j.DiagnosticSeverity;
import ch.epfl.scala.bsp4j.Position;
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams;
import ch.epfl.scala.bsp4j.Range;
import ch.epfl.scala.bsp4j.TextDocumentIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.bazel.rules_scala.diagnostics.Diagnostics;
import io.bazel.rules_scala.diagnostics.Diagnostics.FileDiagnostics;
import io.bazel.rules_scala.diagnostics.Diagnostics.Severity;
import io.bazel.rules_scala.diagnostics.Diagnostics.TargetDiagnostics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.bsp.bazel.common.Uri;

public class BepDiagnosticsDispatcher {

  private static final Logger LOGGER = LogManager.getLogger(BepDiagnosticsDispatcher.class);

  private static final Map<Severity, DiagnosticSeverity> CONVERTED_SEVERITY =
      new ImmutableMap.Builder<Severity, DiagnosticSeverity>()
          .put(Severity.UNKNOWN, DiagnosticSeverity.ERROR)
          .put(Severity.ERROR, DiagnosticSeverity.ERROR)
          .put(Severity.WARNING, DiagnosticSeverity.WARNING)
          .put(Severity.INFORMATION, DiagnosticSeverity.INFORMATION)
          .put(Severity.HINT, DiagnosticSeverity.HINT)
          .build();

  private final BazelBspServer bspServer;
  private final BuildClient bspClient;

  public BepDiagnosticsDispatcher(BazelBspServer bspServer, BuildClient bspClient) {
    this.bspServer = bspServer;
    this.bspClient = bspClient;
  }

  public Map<Uri, List<PublishDiagnosticsParams>> collectDiagnostics(
      BuildTargetIdentifier target, String diagnosticsLocation) {
    try {
      TargetDiagnostics targetDiagnostics =
          TargetDiagnostics.parseFrom(Files.readAllBytes(Paths.get(diagnosticsLocation)));

      return targetDiagnostics.getDiagnosticsList().stream()
          .peek(diagnostics -> LOGGER.info("Collected diagnostics at: {}", diagnostics.getPath()))
          .collect(
              Collectors.toMap(
                  diagnostics -> getUriForPath(diagnostics.getPath()),
                  diagnostics -> convertDiagnostics(target, diagnostics)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void emitDiagnostics(
      Map<Uri, List<PublishDiagnosticsParams>> filesToDiagnostics, BuildTargetIdentifier target) {
    bspServer.getCachedBuildTargetSources(target).stream()
        .map(source -> Uri.fromFileUri(source.getUri()))
        .forEach(sourceUri -> addSourceAndPublish(sourceUri, filesToDiagnostics, target));
  }

  private void addSourceAndPublish(
      Uri sourceUri,
      Map<Uri, List<PublishDiagnosticsParams>> filesToDiagnostics,
      BuildTargetIdentifier target) {
    PublishDiagnosticsParams publishDiagnosticsParams =
        new PublishDiagnosticsParams(
            new TextDocumentIdentifier(sourceUri.toString()), target, ImmutableList.of(), true);

    filesToDiagnostics.putIfAbsent(sourceUri, Lists.newArrayList(publishDiagnosticsParams));

    filesToDiagnostics.values().stream()
        .flatMap(List::stream)
        .forEach(bspClient::onBuildPublishDiagnostics);
  }

  private List<PublishDiagnosticsParams> convertDiagnostics(
      BuildTargetIdentifier target, FileDiagnostics request) {
    List<Diagnostic> diagnostics =
        request.getDiagnosticsList().stream()
            .map(this::convertDiagnostic)
            .collect(Collectors.toList());

    PublishDiagnosticsParams publishDiagnosticsParams =
        new PublishDiagnosticsParams(
            new TextDocumentIdentifier(getUriForPath(request.getPath()).toString()),
            target,
            diagnostics,
            true);

    return Lists.newArrayList(publishDiagnosticsParams);
  }

  private Diagnostic convertDiagnostic(Diagnostics.Diagnostic diagProto) {
    Optional<DiagnosticSeverity> severity =
        Optional.ofNullable(CONVERTED_SEVERITY.get(diagProto.getSeverity()));

    Position startPosition =
        new Position(
            diagProto.getRange().getStart().getLine(),
            diagProto.getRange().getStart().getCharacter());
    Position endPosition =
        new Position(
            diagProto.getRange().getEnd().getLine(), diagProto.getRange().getEnd().getCharacter());
    Range range = new Range(startPosition, endPosition);

    Diagnostic diagnostic = new Diagnostic(range, diagProto.getMessage());
    severity.ifPresent(diagnostic::setSeverity);

    return diagnostic;
  }

  private Uri getUriForPath(String path) {
    return Uri.fromExecOrWorkspacePath(
        path, bspServer.getBazelData().getExecRoot(), bspServer.getBazelData().getWorkspaceRoot());
  }
}
