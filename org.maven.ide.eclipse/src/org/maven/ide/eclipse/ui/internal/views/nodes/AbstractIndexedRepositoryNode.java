/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.ui.internal.views.nodes;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.graphics.Image;

import org.maven.ide.eclipse.MavenImages;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.internal.index.IndexedArtifactGroup;
import org.maven.ide.eclipse.internal.index.NexusIndex;
import org.maven.ide.eclipse.internal.index.NexusIndexManager;


/**
 * AbstractIndexedRepository
 * 
 * @author igor
 */
public abstract class AbstractIndexedRepositoryNode implements IMavenRepositoryNode {

  protected static final Object[] NO_CHILDREN = new Object[0];

  protected final NexusIndexManager indexManager;

  protected final NexusIndex index;
  
  protected AbstractIndexedRepositoryNode(NexusIndexManager indexManager, NexusIndex index) {
    this.indexManager = indexManager;
    this.index = index;
  }

  public Object[] getChildren() {

    if(index == null) {
      return NO_CHILDREN;
    }

    try {
      IndexedArtifactGroup[] rootGroups = indexManager.getRootGroups(index.getIndexName());
      if(rootGroups == null) {
        return NO_CHILDREN;
      }
      IndexedArtifactGroupNode[] children = new IndexedArtifactGroupNode[rootGroups.length];
      for(int i = 0; i < rootGroups.length; i++ ) {
        children[i] = new IndexedArtifactGroupNode(rootGroups[i]);
      }
      return children;
    } catch(CoreException ex) {
      MavenLogger.log(ex);
      return NO_CHILDREN;
    }
  }

  public Image getImage() {
    return MavenImages.IMG_INDEX; 
  }

  public boolean hasChildren() {
    return index != null;
  }

  public boolean isUpdating() {
    return index != null && indexManager.isUpdatingIndex(index.getIndexName());
  }

  public NexusIndex getIndex() {
    return index;
  }

  public abstract String getRepositoryUrl();
}
