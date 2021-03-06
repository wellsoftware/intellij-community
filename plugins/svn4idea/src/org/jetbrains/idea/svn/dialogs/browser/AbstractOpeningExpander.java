/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs.browser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.dialogs.browserCache.KeepingExpandedExpander;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.tree.TreeNode;
import java.util.Enumeration;

public abstract class AbstractOpeningExpander implements Expander {
  private final RepositoryBrowserComponent myBrowser;
  private final KeepingExpandedExpander myKeepingExpander;
  @NotNull private final SVNURL mySelectionPath;

  protected AbstractOpeningExpander(final RepositoryBrowserComponent browser, @NotNull SVNURL selectionPath) {
    myBrowser = browser;
    myKeepingExpander = new KeepingExpandedExpander(browser);
    mySelectionPath = selectionPath;
  }

  public void onBeforeRefresh(final RepositoryTreeNode node) {
    myKeepingExpander.onBeforeRefresh(node);
  }

  protected enum ExpandVariants {
    DO_NOTHING,
    EXPAND_AND_EXIT,
    EXPAND_CONTINUE
  }

  protected abstract ExpandVariants expandNode(@NotNull SVNURL url);

  protected abstract boolean checkChild(@NotNull SVNURL childUrl);

  public void onAfterRefresh(final RepositoryTreeNode node) {
    myKeepingExpander.onAfterRefresh(node);

    if (node.isLeaf()) {
      return;
    }

    final ExpandVariants expandVariant = expandNode(node.getURL());

    if (ExpandVariants.DO_NOTHING.equals(expandVariant)) {
      return;
    }

    // then expanded
    myBrowser.expandNode(node);

    if (ExpandVariants.EXPAND_AND_EXIT.equals(expandVariant)) {
      removeSelf();
    } else {
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        final TreeNode treeNode = (TreeNode) children.nextElement();
        if (treeNode instanceof RepositoryTreeNode) {
          final RepositoryTreeNode repositoryTreeNode = (RepositoryTreeNode) treeNode;
          SVNURL childUrl = repositoryTreeNode.getURL();
          if (checkChild(childUrl)) {
            if (mySelectionPath.equals(childUrl)) {
              myBrowser.setSelectedNode(repositoryTreeNode);
            }
              repositoryTreeNode.reload(this, false);
            return;
          }
        }
      }
      removeSelf();
    }
  }

  private void removeSelf() {
    myBrowser.setLazyLoadingExpander(new KeepingExpandedExpander.Factory());
  }
}
