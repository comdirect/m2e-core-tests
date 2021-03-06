/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.editor.xml;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.ResolverConfiguration;


/**
 * @author atanasenko
 */
public class PluginConfigCompletionTest extends AbstractCompletionTest {

  IProject project;

  protected IFile loadProjectsAndFiles() throws Exception {
    // Create the projects
    setAutoBuilding(true);
    project = importProjects("projects/442560_plugin_content_assist", new String[] {"project/pom.xml"},
        new ResolverConfiguration())[0];
    waitForJobsToComplete();
    return (IFile) project.findMember("pom.xml");
  }
  
  private void initViewer() throws CoreException {
      IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getProject(project);
      assertNotNull(facade);
      assertNotNull(facade.getMavenProject(monitor));
      sourceViewer.setMavenProject(facade.getMavenProject());
  }

  public void testAllMojosCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("<archive>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      
      // 32 parameter proposals, 2 = cdata and processing instr, 1 = wst xml editor thinks that you can also put <project> under <configuration>
      assertEquals("Proposal count", 32 + 3, proposals.length);
  }

  public void testSingleMojoCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("<detail/>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 4 + 3, proposals.length);
  }

  public void testNestedParameterCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("<manifestSections>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 10 + 2, proposals.length);
  }
  
  public void testListItemNameCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("<manifestSection>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 1 + 2, proposals.length);
      
      assertEquals("List item proposal", "manifestSection", proposals[0].getDisplayString());
  }
  
  public void testListItemParameterCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("</manifestSection>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 2 + 2, proposals.length);
      
      assertEquals("List item proposal", "manifestEntries", proposals[0].getDisplayString());
      assertEquals("List item proposal", "name", proposals[1].getDisplayString());
  }
  
  public void testAnyListItemNameParameterCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("</anyItemName>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 2 + 2, proposals.length);
      
      assertEquals("List item proposal", "manifestEntries", proposals[0].getDisplayString());
      assertEquals("List item proposal", "name", proposals[1].getDisplayString());
  }
  
  public void testUnknownContentCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("<anything ");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 2, proposals.length);
  }
  
  public void testImplementationContentCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("</anything>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 14+2, proposals.length);
  }
  
  public void testImplementationListItemNameCompletion() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("</availableVersions>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 1 + 2, proposals.length);
      
      assertEquals("List item proposal", "availableVersion", proposals[0].getDisplayString());
  }
  
  public void testSubclassListItems() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("</modules>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 12 + 2, proposals.length);
      
      assertEquals("Subclass item", "appClientModule", proposals[0].getDisplayString());
      assertEquals("Subclass item", "ejb3Module", proposals[1].getDisplayString());
      assertEquals("Subclass item", "ejbClientModule", proposals[2].getDisplayString());
      assertEquals("Subclass item", "ejbModule", proposals[3].getDisplayString());
      assertEquals("Subclass item", "harModule", proposals[4].getDisplayString());
      assertEquals("Subclass item", "jarModule", proposals[5].getDisplayString());
      assertEquals("Subclass item", "javaModule", proposals[6].getDisplayString());
      assertEquals("Subclass item", "parModule", proposals[7].getDisplayString());
      assertEquals("Subclass item", "rarModule", proposals[8].getDisplayString());
      assertEquals("Subclass item", "sarModule", proposals[9].getDisplayString());
      assertEquals("Subclass item", "webModule", proposals[10].getDisplayString());
      assertEquals("Subclass item", "wsrModule", proposals[11].getDisplayString());
  }
  
  public void testM2ELifecycleMappingConfiguration() throws Exception {
      initViewer();
      
      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      
      String docString = sourceViewer.getDocument().get();
      
      int offset = docString.indexOf("</lifecycleMappingMetadata>");
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 1 + 2, proposals.length);
      assertEquals("List item proposal", "pluginExecutions", proposals[0].getDisplayString());
      
      offset = docString.indexOf("</pluginExecutionFilter>");
      proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 4 + 2, proposals.length);

      offset = docString.indexOf("</execute>");
      proposals = processor.computeCompletionProposals(sourceViewer, offset);
      assertEquals("Proposal count", 2 + 2, proposals.length);
      assertEquals("Execute proposal", "runOnConfiguration", proposals[0].getDisplayString());
      assertEquals("Execute proposal", "runOnIncremental", proposals[1].getDisplayString());

  }
  
  public void testMetadataExtension() throws Exception {
      initViewer();
      
      String docString = sourceViewer.getDocument().get();
      int offset = docString.indexOf("<test1/>");

      PomContentAssistProcessor processor = new PomContentAssistProcessor(sourceViewer);
      ICompletionProposal[] proposals = processor.computeCompletionProposals(sourceViewer, offset);
      
      assertEquals("Proposal count", 2 + 3, proposals.length);
      assertEquals("Extension test1", "test1", proposals[0].getDisplayString());
      assertEquals("Extension test2", "test2", proposals[1].getDisplayString());
  }


}
