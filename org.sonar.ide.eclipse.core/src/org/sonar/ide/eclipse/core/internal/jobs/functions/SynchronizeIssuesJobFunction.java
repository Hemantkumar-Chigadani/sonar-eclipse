/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2015 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.core.internal.jobs.functions;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.sonar.ide.eclipse.common.issues.ISonarIssue;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;
import org.sonar.ide.eclipse.core.internal.markers.MarkerUtils;
import org.sonar.ide.eclipse.core.internal.markers.SonarMarker;
import org.sonar.ide.eclipse.core.internal.remote.EclipseSonar;
import org.sonar.ide.eclipse.core.internal.remote.SourceCode;
import org.sonar.ide.eclipse.core.internal.resources.ResourceUtils;
import org.sonar.ide.eclipse.core.internal.resources.SonarProject;
import org.sonar.ide.eclipse.wsclient.ConnectionException;

/**
 * This class load issues for given source files of a project in background.
 *
 */
@SuppressWarnings("nls")
public class SynchronizeIssuesJobFunction implements IJobFunction {

  private final List<? extends IResource> resources;

  private final boolean force;

  private boolean recursiveVisit = true;

  /**
   * @param resources Resources belonging to a same project.
   * @param force <code>true</code> if forced analysis by user else <code>false</code> for system triggered analysis like auto-build. 
   */
  public SynchronizeIssuesJobFunction(final List<? extends IResource> resources, final boolean force) {
    this.force = force;
    this.resources = resources;
  }

  /**
   * @param recursiveVisit the recursiveVisit to set
   */
  public final void setRecursiveVisit(final boolean recursiveVisit) {
    this.recursiveVisit = recursiveVisit;
  }

  @Override
  public IStatus run(final IProgressMonitor monitor) {
    IStatus status;
    try {
      monitor.beginTask("Synchronize", resources.size());

      for (final IResource resource : resources) {
        if (ResourceUtils.adapt(resource) == null || monitor.isCanceled()) {
          break;
        }
        if (resource.isAccessible() && !MarkerUtils.isResourceLocallyAnalysed(resource)) {
          monitor.subTask(resource.getName());
          resource.accept(new ResourceVisitor(monitor), IResource.NONE);
        }
        monitor.worked(1);
      }

      if (!monitor.isCanceled()) {
        status = Status.OK_STATUS;
      } else {
        status = Status.CANCEL_STATUS;
      }
    } catch (final ConnectionException e) {

      if (force)
      {
        // Forced client might need more info to known what happened here.
        status = new Status(IStatus.ERROR, SonarCorePlugin.PLUGIN_ID, IStatus.ERROR, "Unable to contact SonarQube server", e);
      }
      else
      {
        // Helpful for offline server error debug.
        // http:// jira.sonarsource.com/browse/SONARCLIPS-403
        SonarCorePlugin.getDefault().debug(e.getMessage());
        status = Status.OK_STATUS;
      }

    } catch (final Exception e) {

      status = new Status(IStatus.ERROR, SonarCorePlugin.PLUGIN_ID, IStatus.ERROR, e.getLocalizedMessage(), e);
    } finally {
      monitor.done();
    }
    return status;
  }

  class ResourceVisitor implements IResourceProxyVisitor {
    final IProgressMonitor monitor;

    /**
     * @param monitor
     */
    public ResourceVisitor(final IProgressMonitor monitor) {
      super();
      this.monitor = monitor;
    }

    @Override
    public boolean visit(final IResourceProxy proxy) throws CoreException {
      if (proxy.getType() == IResource.FILE) {
        final IFile file = (IFile) proxy.requestResource();
        retrieveMarkers(file, monitor);
        // do not visit members of this resource
        return false;
      }
      return recursiveVisit;
    }
  }

  private void retrieveMarkers(final IFile resource, final IProgressMonitor monitor) {
    if (resource == null || !resource.exists() || monitor.isCanceled()) {
      return;
    }
    // Handle exceptions other than ConnectionException to avoid recursive delay due to connection failure when offline.
    try
    {
      final SonarProject sonarProject = SonarProject.getInstance(resource.getProject());
      final EclipseSonar eclipseSonar = EclipseSonar.getInstance(resource.getProject());
      if (force || MarkerUtils.needRefresh(resource, sonarProject, eclipseSonar.getSonarServer()))
      {
        final long start = System.currentTimeMillis();
        final SonarCorePlugin sonarCoreDefault = SonarCorePlugin.getDefault();
        sonarCoreDefault.debug("Retrieve issues of resource " + resource.getName() + "...\n");
        final Collection<ISonarIssue> issues = retrieveIssues(eclipseSonar, resource, monitor);
        sonarCoreDefault.debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
        final long startMarker = System.currentTimeMillis();
        sonarCoreDefault.debug("Update markers on resource " + resource.getName() + "...\n");
        MarkerUtils.deleteIssuesMarkers(resource);
        for (final ISonarIssue issue : issues)
        {
          SonarMarker.create(resource, false, issue);
        }
        MarkerUtils.updatePersistentProperties(resource, sonarProject, eclipseSonar.getSonarServer());

        SonarCorePlugin.getDefault().debug("Done in " + (System.currentTimeMillis() - startMarker) + "ms\n");
      }
    } catch (final CoreException coreExp)
    {
      if (force)
      {
        // Forced client might need more info to known what happened here.
        SonarCorePlugin.getDefault().error(coreExp.getMessage(), coreExp);
      }
      else
      {
        // Helpful for offline server error debug.
        // http:// jira.sonarsource.com/browse/SONARCLIPS-403
        SonarCorePlugin.getDefault().debug(coreExp.getMessage());
      }
    }
  }

  private Collection<ISonarIssue> retrieveIssues(final EclipseSonar sonar, final IResource resource, final IProgressMonitor monitor) {
    final SourceCode sourceCode = sonar.search(resource);
    if (sourceCode == null) {
      SonarCorePlugin.getDefault().debug("Unable to find remote resource " + resource.getName() + " on SonarQube server");
      return Collections.emptyList();
    }
    return sourceCode.getRemoteIssuesWithLineCorrection(monitor);
  }

}
