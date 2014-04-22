package edu.oregonstate.cope.eclipse.installer;

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

import edu.oregonstate.cope.clientRecorder.RecorderFacadeInterface;

/**
 * Runs plugin installation mode. This is implemented by storing files both in
 * the eclipse installation path and in the workspace path. When either one does
 * not exist, it is copied there from the other place. When none exists, the one
 * time installation code is run.
 * 
 */
public class EclipseInstaller extends Installer {
	public EclipseInstaller(RecorderFacadeInterface recorder, InstallerHelper installerHelper) {
		this.recorder = recorder;
		this.installerHelper = installerHelper;
	}

	protected ArrayList<InstallerOperation> getInstallOperations() {
		ArrayList<InstallerOperation> installerOperations = new ArrayList<>();

		IConfigurationElement[] extensions = Platform.getExtensionRegistry().getConfigurationElementsFor(INSTALLER_EXTENSION_ID);
		for (IConfigurationElement extension : extensions) {
			try {
				Object executableExtension = extension.createExecutableExtension("InstallerOperation");
				if (executableExtension instanceof InstallerOperation) {
					installerOperations.add((InstallerOperation) executableExtension);
				}
			} catch (CoreException e) {
				System.out.println(e);
			}
		}
		
		return installerOperations;
	}
}
