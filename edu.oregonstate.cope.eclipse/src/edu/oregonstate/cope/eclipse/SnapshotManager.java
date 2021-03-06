package edu.oregonstate.cope.eclipse;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.ui.internal.wizards.datatransfer.ArchiveFileExportOperation;

import edu.oregonstate.cope.clientRecorder.ClientRecorder;
import edu.oregonstate.cope.clientRecorder.ProjectManager;

public class SnapshotManager {

	private ProjectManager projectManager;
	private ClientRecorder clientRecorder;
	private String parentDirectory;

	protected SnapshotManager(String parentDirectory) {
		this.parentDirectory = parentDirectory;
		projectManager = new ProjectManager(parentDirectory, COPEPlugin.getDefault().getLogger());
		clientRecorder = COPEPlugin.getDefault().getClientRecorder();
	}

	public boolean isProjectKnown(IProject project) {
		if (project == null)
			return true;
		return projectManager.isProjectKnown(project.getName());
	}
	
	private void knowProject(IProject project) {
		projectManager.knowProject(project.getName());
	}

	@SuppressWarnings("restriction")
	public String takeSnapshot(final IProject project) {
		if (!isProjectKnown(project))
			knowProject(project);
		
		final String zipFileName = project.getName() + "-" + System.currentTimeMillis() + ".zip";
		final String zipFile = parentDirectory + File.separator + zipFileName;
		Job snapshotJob = new Job("Taking snapshot of " + project.getName()) {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask("Taking snapshot of " + project.getName(), 1);
				archiveProjectToFile(project, zipFile);
				clientRecorder.recordSnapshot(zipFileName);
				if (JavaProject.hasJavaNature(project)) {
					IJavaProject javaProject = addExternalLibrariesToZipFile(project, zipFile);
					snapshotRequiredProjects(javaProject);
				}
				monitor.done();
				return Status.OK_STATUS;
			}
		};
		snapshotJob.setRule(project);
		snapshotJob.schedule();
		return zipFile;
	}

	private synchronized IJavaProject addExternalLibrariesToZipFile(IProject project, String zipFile) {
		IJavaProject javaProject = JavaCore.create(project);
		List<String> nonWorkspaceLibraries = getNonWorkspaceLibraries(javaProject);
		addLibsToZipFile(nonWorkspaceLibraries, zipFile);
		return javaProject;
	}

	@SuppressWarnings("restriction")
	private synchronized void archiveProjectToFile(IProject project, String zipFile) {
		ArchiveFileExportOperation archiveFileExportOperation = new ArchiveFileExportOperation(project, zipFile);
		archiveFileExportOperation.setUseCompression(true);
		archiveFileExportOperation.setUseTarFormat(false);
		archiveFileExportOperation.setCreateLeadupStructure(true);
		try {
			archiveFileExportOperation.run(new NullProgressMonitor());
		} catch (InvocationTargetException | InterruptedException e) {
			COPEPlugin.getDefault().getLogger().error(this, e.getMessage(), e);
		}
	}

	private void snapshotRequiredProjects(IJavaProject javaProject) {
		try {
			String[] requiredProjectNames = javaProject.getRequiredProjectNames();
			for (String requiredProjectName : requiredProjectNames) {
				if(!projectManager.isProjectKnown(requiredProjectName) && !isProjectIgnored(requiredProjectName))
					takeSnapshot(requiredProjectName);
			}
		} catch (JavaModelException | IllegalArgumentException e) {
			COPEPlugin.getDefault().getLogger().error(this, "The weird problem", e);
		}
	}

	private boolean isProjectIgnored(String requiredProjectName) {
		return COPEPlugin.getDefault().getIgnoreProjectsList().contains(requiredProjectName);
	}

	private String takeSnapshot(String projectName) {
		IProject requiredProject = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		return takeSnapshot(requiredProject);
	}

	public List<String> getNonWorkspaceLibraries(IJavaProject project) {
		IClasspathEntry[] resolvedClasspath = null;
		try {
			resolvedClasspath = project.getRawClasspath();
		} catch (JavaModelException e) {
			COPEPlugin.getDefault().getLogger().error(this, e.getMessage(), e);
			return new ArrayList<String>();
		}
		List<String> pathsOfLibraries = new ArrayList<String>();
		for (IClasspathEntry iClasspathEntry : resolvedClasspath) {
			if (iClasspathEntry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
				pathsOfLibraries.add(iClasspathEntry.getPath().toPortableString());
			}
		}
		return pathsOfLibraries;
	}
	
	public void addLibsToZipFile(List<String> pathOfLibraries, String zipFilePath) {
		try {
			String libFolder = "libs" + File.separator;
			ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFilePath+"-libs", true));
			copyExistingEntries(zipFilePath, zipOutputStream);
			for (String library : pathOfLibraries) {
				Path path = Paths.get(library);
				if(!Files.exists(path)) //if the project is in the workspace
					continue;
				ZipEntry libraryZipEntry = new ZipEntry(libFolder + path.getFileName());
				zipOutputStream.putNextEntry(libraryZipEntry);
				byte[] libraryContents = Files.readAllBytes(path);
				zipOutputStream.write(libraryContents);
			}
			zipOutputStream.close();
			new File(zipFilePath).delete();
			new File(zipFilePath+"-libs").renameTo(new File(zipFilePath));
		} catch (IOException e) {
			COPEPlugin.getDefault().getLogger().error(this, e.getMessage(), e);
		} finally {
		}
	}

	private void copyExistingEntries(String zipFilePath, ZipOutputStream zipOutputStream) {
		try {
			ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath));
			ZipFile zipFile = new ZipFile(zipFilePath);
			while(zipInputStream.available() == 1) {
				ZipEntry entry = zipInputStream.getNextEntry();
				if (entry == null)
					continue;
				zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
				int blockSize = 1024;
				BufferedInputStream entryInputStream = new BufferedInputStream(zipFile.getInputStream(entry));
				do {
					byte[] contents = new byte[blockSize];
					int read = entryInputStream.read(contents, 0, blockSize);
					zipOutputStream.write(contents);
					if (read == -1)
						break;
				} while (true);
			}
			zipInputStream.close();
		} catch (IOException e) {
			COPEPlugin.getDefault().getLogger().error(this, e.getMessage(), e);
		}
	}
	
	protected void takeSnapshotOfSessionTouchedProjects() {
		for (String project : projectManager.sessionTouchedProjects) {
			takeSnapshot(project);
		}
	}
	
	protected void takeSnapshotOfKnownProjects() {
		for (String project : projectManager.knownProjects) {
			takeSnapshot(project);
		}
	}
	
	public ProjectManager getProjectManager() {
		return projectManager;
	}
}
