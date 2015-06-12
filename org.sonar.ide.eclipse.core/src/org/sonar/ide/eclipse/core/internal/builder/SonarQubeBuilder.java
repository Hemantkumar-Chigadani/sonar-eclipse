package org.sonar.ide.eclipse.core.internal.builder;

import java.util.Collections;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.sonar.ide.eclipse.core.internal.jobs.AnalyzeProjectRequest;
import org.sonar.ide.eclipse.core.internal.jobs.SonarQubeAnalysisJob;

public class SonarQubeBuilder extends IncrementalProjectBuilder {

  @Override
  protected IProject[] build(int kind, Map args, IProgressMonitor monitor) {
    if (kind == IncrementalProjectBuilder.FULL_BUILD) {
      fullBuild(monitor);
    } else {
      IResourceDelta delta = getDelta(getProject());
      if (delta == null) {
        fullBuild(monitor);
      } else {
        incrementalBuild(delta, monitor);
      }
    }
    return null;
  }

  private void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) {
    System.out.println("incremental build on " + delta);
    try {
      delta.accept(new IResourceDeltaVisitor() {
        @Override
        public boolean visit(IResourceDelta delta) {
          IResource resource = delta.getResource();
          if (resource.isDerived()) {
            return false;
          }
          IFile file = (IFile) resource.getAdapter(IFile.class);
          if (file == null) {
            return true;
          }
          System.out.println("changed: " + file.getRawLocation());
          IProject project = resource.getProject();
          AnalyzeProjectRequest request = new AnalyzeProjectRequest(file)
            // // FIXME .setDebugEnabled(false)
            .useHttpWsCache(true);
          // // FIXME .setExtraProps(SonarUiPlugin.getExtraPropertiesForLocalAnalysis(project));
          new SonarQubeAnalysisJob(Collections.singletonList(request)).schedule();
          return true; // visit children too
        }
      });
    } catch (CoreException e) {
      e.printStackTrace();
    }
  }

  private void fullBuild(IProgressMonitor monitor) {
    System.out.println("full build");
  }
}
