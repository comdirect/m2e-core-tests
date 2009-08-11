/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.internal.embedder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.codehaus.plexus.MutablePlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.embedder.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuildingResult;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.validation.SettingsValidationResult;

import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.IMavenConstants;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.embedder.IMaven;
import org.maven.ide.eclipse.embedder.IMavenConfiguration;
import org.maven.ide.eclipse.index.IndexManager;


public class MavenImpl implements IMaven {

  private final PlexusContainer plexus;

  private final Maven maven;

  private final ProjectBuilder projectBuilder;

  private final ModelReader modelReader;

  private final ModelWriter modelWriter;

  private final RepositorySystem repositorySystem;

  private final MavenSettingsBuilder settingsBuilder;

  private final IMavenConfiguration mavenConfiguration;

  private final MavenExecutionRequestPopulator populator;

  private final PluginManager pluginManager;

  private final LifecycleExecutor lifecycleExecutor;

  private final ConverterLookup converterLookup = new DefaultConverterLookup();

  private MavenConsole console;

  private IndexManager indexManager;

  public MavenImpl(PlexusContainer plexus, IMavenConfiguration mavenConfiguration) throws CoreException {
    this.plexus = plexus;
    try {
      this.maven = plexus.lookup(Maven.class);
      this.projectBuilder = plexus.lookup(ProjectBuilder.class);
      this.modelReader = plexus.lookup(ModelReader.class);
      this.modelWriter = plexus.lookup(ModelWriter.class);
      this.repositorySystem = plexus.lookup(RepositorySystem.class);
      this.settingsBuilder = plexus.lookup(MavenSettingsBuilder.class);
      this.mavenConfiguration = mavenConfiguration;
      this.populator = plexus.lookup(MavenExecutionRequestPopulator.class);
      this.pluginManager = plexus.lookup(PluginManager.class);
      this.lifecycleExecutor = plexus.lookup(LifecycleExecutor.class);
    } catch(ComponentLookupException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not lookup required component", ex));
    }
  }

  public MavenExecutionRequest createExecutionRequest(IProgressMonitor monitor) throws CoreException {
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    if(mavenConfiguration.getGlobalSettingsFile() != null) {
      request.setGlobalSettingsFile(new File(mavenConfiguration.getGlobalSettingsFile()));
    }
    if(mavenConfiguration.getUserSettingsFile() != null) {
      request.setUserSettingsFile(new File(mavenConfiguration.getUserSettingsFile()));
    }
    ArtifactRepository localRepository = getLocalRepository();
    request.setLocalRepository(localRepository);
    request.setLocalRepositoryPath(localRepository.getBasedir());
    request.setOffline(mavenConfiguration.isOffline());

    // logging
    request.setTransferListener(new TransferListenerAdapter(monitor, console, indexManager));

    request.getUserProperties().put("m2e.version", MavenPlugin.getVersion());

    // the right way to disable snapshot update
    // request.setUpdateSnapshots(false);
    return request;
  }

  private String getLocalRepositoryPath() throws CoreException {
    return getSettings().getLocalRepository();
  }

  public MavenExecutionResult execute(MavenExecutionRequest request, IProgressMonitor monitor) {
    // XXX is there a way to set per-request log level?

    MavenExecutionResult result;
    try {
      populator.populateDefaults(request);
      result = maven.execute(request);
    } catch(MavenEmbedderException ex) {
      ex.printStackTrace();
      result = new DefaultMavenExecutionResult();
      result.addException(ex);
    } catch (Exception e){
      e.printStackTrace();
      result = new DefaultMavenExecutionResult();
      result.addException(e);
    }
    if(result.getExceptions() != null && result.getExceptions().size() > 0){
      for(Exception e :result.getExceptions()){
        e.printStackTrace();
      }
    }
    return result;
  }

  public MavenSession createSession(MavenExecutionRequest request, MavenProject project) {
    MavenExecutionResult result = new DefaultMavenExecutionResult();
    return new MavenSession(plexus, request, result, project);
  }

  public void execute(MavenSession session, MojoExecution execution, IProgressMonitor monitor) {
    try {
      pluginManager.executeMojo(session, execution);
    } catch(Exception ex) {
      session.getResult().addException(ex);
    }
  }

  public MavenExecutionPlan calculateExecutionPlan(MavenExecutionRequest request, MavenProject project,
      IProgressMonitor monitor) throws CoreException {
    MavenSession session = createSession(request, project);
    try {
      List<String> goals = request.getGoals();
      return lifecycleExecutor.calculateExecutionPlan(session, goals.toArray(new String[goals.size()]));
    } catch(Exception ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not calculate build plan", ex));
    }
  }

  public ArtifactRepository getLocalRepository() throws CoreException {
    try {
      String localRepositoryPath = getLocalRepositoryPath();
      if(localRepositoryPath != null) {
        return repositorySystem.createLocalRepository(new File(localRepositoryPath));
      }
      return repositorySystem.createLocalRepository(RepositorySystem.defaultUserLocalRepository);
    } catch(InvalidRepositoryException ex) {
      // can't happen
      throw new IllegalStateException(ex);
    }
  }

  public Settings getSettings() throws CoreException {
    // MUST NOT use createRequest!

    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    if(mavenConfiguration.getGlobalSettingsFile() != null) {
      request.setGlobalSettingsFile(new File(mavenConfiguration.getGlobalSettingsFile()));
    }
    if(mavenConfiguration.getUserSettingsFile() != null) {
      request.setUserSettingsFile(new File(mavenConfiguration.getUserSettingsFile()));
    }
    try {
      return settingsBuilder.buildSettings(request);
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read settings.xml",
          ex));
    } catch(XmlPullParserException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read settings.xml",
          ex));
    }
  }

  public Settings buildSettings(String globalSettings, String userSettings) throws CoreException {
    MavenExecutionRequest request = createExecutionRequest(null);
    request.setGlobalSettingsFile(globalSettings != null ? new File(globalSettings) : null);
    request.setUserSettingsFile(userSettings != null ? new File(userSettings) : null);
    try {
      return settingsBuilder.buildSettings(request);
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read settings.xml",
          ex));
    } catch(XmlPullParserException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read settings.xml",
          ex));
    }
  }

  public SettingsValidationResult validateSettings(String settings) {
    SettingsValidationResult result = new SettingsValidationResult();
    if(settings != null) {
      File settingsFile = new File(settings);
      if(settingsFile.canRead()) {
        @SuppressWarnings("unchecked")
        List<String> messages = settingsBuilder.validateSettings(settingsFile).getMessages();
        for(String message : messages) {
          result.addMessage(message);
        }
      } else {
        result.addMessage("Can not read settings file " + settings);
      }
    }

    return result;
  }

  public Model readModel(InputStream in) throws CoreException {
    try {
      return modelReader.read(in, null);
    } catch(IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read pom.xml", e));
    }
  }

  public Model readModel(File pomFile) throws CoreException {
    try {
      BufferedInputStream is = new BufferedInputStream(new FileInputStream(pomFile));
      try {
        return readModel(is);
      } finally {
        IOUtil.close(is);
      }
    } catch(IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read pom.xml", e));
    }
  }

  public void writeModel(Model model, OutputStream out) throws CoreException {
    try {
      modelWriter.write(out, null, model);
    } catch(IOException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not write pom.xml", ex));
    }
  }

  public MavenProject readProject(File pomFile, IProgressMonitor monitor) throws CoreException {
    try {
      MavenExecutionRequest request = createExecutionRequest(monitor);
      populator.populateDefaults(request);
      ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
      configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      return projectBuilder.build(pomFile, configuration);
    } catch(ProjectBuildingException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read maven project",
          ex));
    } catch(MavenEmbedderException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, "Could not read maven project",
          ex));
    }
  }

  public MavenExecutionResult readProjectWithDependencies(MavenExecutionRequest request, IProgressMonitor monitor) {
    File pomFile = request.getPom();
    MavenExecutionResult result = new DefaultMavenExecutionResult();
    MavenProjectBuildingResult projectBuildingResult;
    try {
      populator.populateDefaults(request);
      ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
      configuration.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      projectBuildingResult = projectBuilder.buildProjectWithDependencies(pomFile, configuration);
      result.setProject(projectBuildingResult.getProject());
      result.setArtifactResolutionResult(projectBuildingResult.getArtifactResolutionResult());
    } catch(ProjectBuildingException ex) {
      return result.addException(ex);
    } catch(MavenEmbedderException ex) {
      return result.addException(ex);
    }
    return result;
  }

  public Artifact resolve(String groupId, String artifactId, String version, String type, String classifier,
      List<ArtifactRepository> remoteRepositories, IProgressMonitor monitor) throws CoreException {
    Artifact artifact = repositorySystem.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);

    ArtifactResolutionRequest request = new ArtifactResolutionRequest();
    request.setLocalRepository(getLocalRepository());
    if(remoteRepositories != null) {
      request.setRemoteRepostories(remoteRepositories);
    } else {
      try {
        MavenExecutionRequest er = new DefaultMavenExecutionRequest();
        populator.populateDefaults(er);
        request.setRemoteRepostories(er.getRemoteRepositories());
      } catch(MavenEmbedderException e) {
        // we've tried
        request.setRemoteRepostories(new ArrayList<ArtifactRepository>());
      }
    }
    request.setArtifact(artifact);

    ArtifactResolutionResult result = repositorySystem.resolve(request);

    if(result.hasExceptions()) {
      ArrayList<IStatus> members = new ArrayList<IStatus>();
      for(Exception e : result.getExceptions()) {
        members.add(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1, e.getMessage(), e));
      }
      IStatus[] newMembers = members.toArray(new IStatus[members.size()]);
      throw new CoreException(new MultiStatus(IMavenConstants.PLUGIN_ID, -1, newMembers, "Could not resolve artifact",
          null));
    }

    return artifact;
  }

  public <T> T getMojoParameterValue(MavenSession session, MojoExecution mojoExecution, String parameter,
      Class<T> asType) throws CoreException {

    try {
      MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

      ClassRealm pluginRealm = pluginManager.getPluginRealm(session, mojoDescriptor.getPluginDescriptor());

      ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);

      ConfigurationConverter typeConverter = converterLookup.lookupConverterForType(asType);

      Xpp3Dom dom = mojoExecution.getConfiguration();

      if(dom == null) {
        return null;
      }

      PlexusConfiguration pomConfiguration = new XmlPlexusConfiguration(dom);

      PlexusConfiguration configuration = pomConfiguration.getChild(parameter);

      if(configuration == null) {
        return null;
      }

      Object value = typeConverter.fromConfiguration(converterLookup, configuration, asType, mojoDescriptor
          .getImplementationClass(), pluginRealm, expressionEvaluator, null);
      return asType.cast(value);
    } catch(ComponentConfigurationException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get mojo execution paramater value", ex));
    } catch(PluginManagerException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not get mojo execution paramater value", ex));
    }
  }

  public void xxxRemoveExtensionsRealm(MavenProject project) {
    ClassRealm realm = project.getClassRealm();
    if(realm != null && realm != plexus.getContainerRealm()) {
      ClassWorld world = ((MutablePlexusContainer) plexus).getClassWorld();
      try {
        world.disposeRealm(realm.getId());
      } catch(NoSuchRealmException ex) {
        MavenLogger.log("Could not dispose of project extensions class realm", ex);
      }
    }
  }

  public List<ArtifactRepository> getArtifactRepositories() throws CoreException {
    return createPopulatedExecutionRequest().getRemoteRepositories();
  }

  private MavenExecutionRequest createPopulatedExecutionRequest() throws CoreException {
    MavenExecutionRequest request = createExecutionRequest(new NullProgressMonitor());
    try {
      populator.populateDefaults(request);
    } catch(MavenEmbedderException ex) {
      throw new CoreException(new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, -1,
          "Could not read Maven configuration", ex));
    }
    return request;
  }
  
  public List<ArtifactRepository> getPluginArtifactRepository() throws CoreException {
    return createPopulatedExecutionRequest().getPluginArtifactRepositories();
  }

  public List<String> getMirrorUrls() throws CoreException {
    ArrayList<String> result = new ArrayList<String>();
    for(Mirror mirror : getSettings().getMirrors()) {
      result.add(mirror.getUrl());
    }
    return result;
  }

  public List<ArtifactRepository> getEffectiveRepositories(List<ArtifactRepository> repositories) {
    return repositorySystem.getEffectiveRepositories(repositories);
  }

  /*
   * This is not exactly nice, MavenImpl and NexusIndexManager depend on each other.
   * 
   * Ideally, we need to split the code that reads maven static configuration
   * in a separate interface.
   */
  public void setIndexManager(IndexManager indexManager) {
    this.indexManager = indexManager;
  }
}
