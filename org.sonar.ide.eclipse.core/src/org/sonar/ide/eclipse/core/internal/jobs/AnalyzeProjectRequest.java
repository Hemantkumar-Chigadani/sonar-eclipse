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
package org.sonar.ide.eclipse.core.internal.jobs;

import java.util.Collections;
import java.util.List;
import org.eclipse.core.resources.IResource;
import org.sonar.ide.eclipse.core.internal.resources.SonarProperty;

public class AnalyzeProjectRequest {

  private IResource resource;
  private boolean debugEnabled;
  private List<SonarProperty> extraProps = Collections.emptyList();
  private boolean useHttpWsCache = true;

  public AnalyzeProjectRequest(IResource resource) {
    this.resource = resource;
  }

  public IResource getResource() {
    return resource;
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public AnalyzeProjectRequest setDebugEnabled(boolean debugEnabled) {
    this.debugEnabled = debugEnabled;
    return this;
  }

  public List<SonarProperty> getExtraProps() {
    return extraProps;
  }

  public AnalyzeProjectRequest setExtraProps(List<SonarProperty> extraProps) {
    this.extraProps = extraProps;
    return this;
  }

  public boolean useHttpWsCache() {
    return useHttpWsCache;
  }

  public AnalyzeProjectRequest useHttpWsCache(boolean useHttpWsCache) {
    this.useHttpWsCache = useHttpWsCache;
    return this;
  }

}
