package edu.uncc.sis.aside.jobs;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;

import edu.uncc.sis.aside.AsidePlugin;

public class ESAPIConfigurationJob extends Job {

	private final static String ESAPI_CONFIG_DIR_NAME = "ASIDE-ESAPI";
	private final static String ASIDE_ESAPI_CONTAINER = "ESAPI Libraries";
	private final static String PROJECT_LIB_PATH = "WebContent"
			+ IPath.SEPARATOR + "WEB-INF" + IPath.SEPARATOR + "lib";
	private final static String PROJECT_WEBINF_PATH = "src" ;//"WebContent" + IPath.SEPARATOR + "WEB-INF";
	private final static String ESAPI_VM_ARG = "-Dorg.owasp.esapi.resources";

	private IProject fProject;
	private IJavaProject javaProject;

	public ESAPIConfigurationJob(String name, IProject project,
			IJavaProject javaProject) {
		super(name);
		this.fProject = project;
		this.javaProject = javaProject;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		
		
		
		if (monitor == null)
			monitor = new NullProgressMonitor();

		return configure(monitor);
	}

	private IStatus configure(IProgressMonitor monitor) {
		if(AsidePlugin.getDefault().isInternetReachable()){

		/**
		 * Apparently, here's the assumption that the target project follows the
		 * dynamic web project structure defined by Eclipse
		 */

		final IFolder lib = fProject.getFolder(PROJECT_LIB_PATH);
		if (!lib.exists()) {
			System.out.println("Cannot find: " + lib);
			return Status.OK_STATUS;
		}

		Bundle bundle = Platform.getBundle(AsidePlugin.PLUGIN_ID);
		Path path = new Path(IPath.SEPARATOR + ESAPI_CONFIG_DIR_NAME);
		URL fileURL = FileLocator.find(bundle, path, null);
		if (fileURL == null) {
			System.out.println("cannot locate ESAPI directory");
			return Status.OK_STATUS;
		}
        //System.out.println("FileLocator.find(bundle, path, null)--fileURL=="+fileURL);
		InputStream is = null;
		try {

			URL localFileURL = FileLocator.toFileURL(fileURL);

			String sourcePath = localFileURL.getFile();
			File file = new File(sourcePath);
			if (file.exists() && file.isDirectory()) {
				File[] sourceFiles = file.listFiles();
				monitor.beginTask("Configuring OWASP ESAPI for Java Project: "
						+ fProject.getName(), IProgressMonitor.UNKNOWN);
				for (int i = 0; i < sourceFiles.length; i++) {

					File target = sourceFiles[i];
					String fileName = target.getName();
					monitor.subTask("Checking and Copying ESAPI library: "
							+ fileName);

					if (target.isFile()) {
						if (fileName.endsWith(".jar")) {
							is = new BufferedInputStream(new FileInputStream(
									target.getAbsolutePath()));
							IFile destination = fProject
									.getFile(IPath.SEPARATOR + PROJECT_LIB_PATH
											+ IPath.SEPARATOR + fileName);
							
							if (!destination.exists()) {
								try {
									destination.create(is, false, null);
								} catch (CoreException e) {
									continue;
								}
							}
						}else if(fileName.equals("log4j.properties")){ //copy the log4j.properties file
							is = new BufferedInputStream(new FileInputStream(
									target.getAbsolutePath()));
							IFile destination = fProject
									.getFile(IPath.SEPARATOR + PROJECT_WEBINF_PATH
											+ IPath.SEPARATOR + fileName);
							
							if (!destination.exists()) {
								try {
									destination.create(is, false, null);
								} catch (CoreException e) {
									continue;
								}
							}
						}
					} else if (target.isDirectory()) {
						if (fileName.equalsIgnoreCase("esapi")
								|| fileName.equalsIgnoreCase(".esapi")) {
							//IFolder destination = fProject.getFolder(fileName); // this one may need modification
							//added Mar. 2
							//IFolder tmp = fProject.getFolder(fileName);
							IFolder destination = fProject.getFolder(IPath.SEPARATOR + PROJECT_WEBINF_PATH + IPath.SEPARATOR + fileName);
							String tmp = IPath.SEPARATOR + PROJECT_WEBINF_PATH + IPath.SEPARATOR + fileName;
							//System.out.println("line 126 destination = " + destination + " tmp" + tmp);
							/////
							
							if (!destination.exists()) {
								try {
									destination.create(true, true, null);
								} catch (CoreException e) {
									continue;
								}
							}
							copyDirectory(target, tmp, is, monitor);
						}
					}
					monitor.worked(1);
				}

				if (monitor.isCanceled())
					return Status.CANCEL_STATUS;
			} else {
				System.out.println("file does not exist");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JavaModelException e) {
			e.printStackTrace();
		} catch (CoreException e) {
			e.printStackTrace();
		} finally {
			monitor.done();
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// setESAPIClasspathContainer(lib);
		setESAPIResourceLocation();
		}
		return Status.OK_STATUS;
	}

	private void copyDirectory(File sourceDir, String basePath, InputStream is,
			IProgressMonitor monitor) throws CoreException,
			FileNotFoundException {
       // System.out.println("basePath " + basePath);
		File[] subFiles = sourceDir.listFiles();
		for (File subFile : subFiles) {

			String sub_basePath = basePath + IPath.SEPARATOR
					+ subFile.getName();
			monitor.subTask("Copying file " + sub_basePath);
			if (subFile.isDirectory()) {

				IFolder destination = fProject.getFolder(sub_basePath);
				if (!destination.exists()) {
					try {
						destination.create(true, true, null);
					} catch (CoreException e) {
						continue;
					}
				}
				copyDirectory(subFile, sub_basePath, is, monitor);

			} else if (subFile.isFile()) {
				is = new BufferedInputStream(new FileInputStream(
						subFile.getAbsolutePath()));
				IFile destination = fProject.getFile(sub_basePath);
				if (!destination.exists()) {
					try {
						destination.create(is, false, null);
					} catch (CoreException e) {
						continue;
					}
				}
			}
			monitor.worked(1);
		}
	}

	private void setESAPIClasspathContainer(final IFolder lib) {
		final IPath containerPath = new Path(ASIDE_ESAPI_CONTAINER)
				.append(fProject.getFullPath());
		IClasspathContainer esapiContainer = new IClasspathContainer() {

			@Override
			public IClasspathEntry[] getClasspathEntries() {
				ArrayList<IClasspathEntry> entryList = new ArrayList<IClasspathEntry>();
				try {
					IResource[] members = lib.members();
					for (IResource resource : members) {
						if (IFile.class.isAssignableFrom(resource.getClass())) {
							if (resource.getName().endsWith(".jar")) {
								entryList.add(JavaCore.newLibraryEntry(
										new Path(resource.getFullPath()
												.toOSString()), null, new Path(
												"/")));
							}
						}
					}
				} catch (CoreException e) {
					e.printStackTrace();
				}
				// convert the list to an array and return it
				IClasspathEntry[] entryArray = new IClasspathEntry[entryList
						.size()];
				return entryList.toArray(entryArray);
			}

			@Override
			public String getDescription() {
				return "ASIDE ESAPI Libraries";
			}

			@Override
			public int getKind() {
				return IClasspathEntry.CPE_CONTAINER;
			}

			@Override
			public IPath getPath() {
				return containerPath;
			}

			@Override
			public String toString() {
				return getDescription();
			}
		};

		try {
			JavaCore.setClasspathContainer(containerPath,
					new IJavaProject[] { javaProject },
					new IClasspathContainer[] { esapiContainer }, null);

			IClasspathEntry[] entries = javaProject.getRawClasspath();

			// check if the container is already on the path
			boolean hasEsapiContainer = false;

			for (int i = 0; i < entries.length; i++) {
				if (entries[i].getEntryKind() == IClasspathEntry.CPE_CONTAINER
						&& entries[i].getPath().equals(containerPath)) {
					hasEsapiContainer = true;
				}
			}
			if (!hasEsapiContainer) {
				IClasspathEntry[] newEntries = new IClasspathEntry[entries.length + 1];

				System.arraycopy(entries, 0, newEntries, 0, entries.length);

				// add a new entry using the path to the container
				newEntries[entries.length] = JavaCore
						.newContainerEntry(esapiContainer.getPath());

				javaProject.setRawClasspath(newEntries, null);

			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}

	}

	private void setESAPIResourceLocation() {
		URI locationUri = null;
		IFolder folder = fProject.getFolder(PROJECT_WEBINF_PATH + IPath.SEPARATOR + "esapi"); //here may need changes

		if (folder.exists()) {
			//System.out.println("setESAPIResourceLocation class--folder.exists()="+locationUri);
			locationUri = folder.getRawLocationURI();
			//System.out.println("setESAPIResourceLocation class--older.getRawLocationURI="+locationUri);
		} else {
			folder = fProject.getFolder(".esapi");
			System.out.println("folder not exist!");
			
			if (folder.exists()) {
				locationUri = folder.getRawLocationURI();
				//System.out.println("setESAPIResourceLocation class-- fProject.getFolder .esapi exist, locationUri="+locationUri);
				
			}
		}

		if (locationUri == null){
			System.out.println("setESAPIResourceLocation class--locationUri= null");
			return;
		}
		//String path = ESAPI_VM_ARG + "=\"" + locationUri.getPath().substring(1) + "\""; just added for test Feb. 28
		
		String path = ESAPI_VM_ARG + "=\"" + "/" + locationUri.getPath().substring(1) + "\"";
		
        //System.out.println("Line 307 Path = " + path);
		try {
			AbstractVMInstall vminstall = (AbstractVMInstall) JavaRuntime
					.getVMInstall(javaProject);
			if (vminstall != null) {
				String[] vmargs = vminstall.getVMArguments();
				if (vmargs == null) {
					vminstall.setVMArguments(new String[] { path });
				    System.out.println("vmargs == null, and it is reseted, Line 315 Path = " + path);
				}
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	public void scheduleInteractive() {
		setUser(true);
		setPriority(Job.INTERACTIVE);
		schedule();
	}
}