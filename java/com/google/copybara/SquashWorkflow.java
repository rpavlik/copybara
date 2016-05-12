package com.google.copybara;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.copybara.Origin.ReferenceFiles;
import com.google.copybara.transform.Transformation;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;

import javax.annotation.Nullable;

/**
 * Migrates all pending changes as one change.
 */
public class SquashWorkflow<T extends Origin<T>> extends Workflow<T> {

  private final String configName;
  private final String lastRevision;
  private final boolean includeChangeListNotes;

  SquashWorkflow(String configName, String workflowName, Origin<T> origin, Destination destination,
      ImmutableList<Transformation> transformations, Console console, String lastRevision,
      boolean includeChangeListNotes) {
    super(workflowName, origin, destination, transformations, console);
    this.configName = configName;
    this.lastRevision = lastRevision;
    this.includeChangeListNotes = includeChangeListNotes;
  }

  @Override
  public void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException {
    console.progress("Resolving " + ((sourceRef == null) ? "origin reference" : sourceRef));
    ReferenceFiles<T> resolvedRef = getOrigin().resolve(sourceRef);
    logger.log(Level.INFO,
        "Running Copybara for config '" + configName
            + "', workflow '" + getName()
            + "' (" + WorkflowMode.SQUASH + ")"
            + " and ref '" + resolvedRef.asString() + "': " + this.toString());
    resolvedRef.checkout(workdir);

    runTransformations(workdir);

    Long timestamp = resolvedRef.readTimestamp();
    if (timestamp == null) {
      timestamp = System.currentTimeMillis() / 1000;
    }
    getDestination().process(workdir, resolvedRef, timestamp, getCommitMessage(resolvedRef));
  }

  private String getCommitMessage(ReferenceFiles<T> resolvedRef) throws RepoException {
    return String.format(
        "Imports '%s'.\n\n"
            + "This change was generated by Copybara (go/copybara).\n%s\n", configName,
        getChangeListNotes(resolvedRef));
  }

  private String getChangeListNotes(ReferenceFiles<T> resolvedRef) throws RepoException {
    if (!includeChangeListNotes) {
      return "";
    }
    String previousRef = getPreviousRef();
    if (Strings.isNullOrEmpty(previousRef)) {
      return "";
    }
    ImmutableList<Change<T>> changes;
    try {
      changes = getOrigin().changes(getOrigin().resolve(previousRef), resolvedRef);
    } catch (RepoException e) {
      logger.log(Level.WARNING, "Previous reference couldn't be resolved", e);
      return "(List of included changes could not be computed)\n";
    }
    StringBuilder result = new StringBuilder("List of included changes:\n");
    for (Change<T> change : changes) {
      result.append(String.format("  - %s %s by %s\n",
          change.getReference().asString(),
          change.firstLineMessage(),
          change.getAuthor()));
    }

    return result.toString();
  }

  private String getPreviousRef() throws RepoException {
    if (!Strings.isNullOrEmpty(lastRevision)) {
      return lastRevision;
    }
    return getDestination().getPreviousRef(getOrigin().getLabelName());
  }
}
