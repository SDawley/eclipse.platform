package org.eclipse.update.internal.security;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.jar.*;

import org.eclipse.core.runtime.*;
import org.eclipse.update.internal.core.Policy;
import org.eclipse.update.internal.core.UpdateManagerPlugin;

/**
 * The JarVerifier will check the integrity of the JAR.
 * If the Jar is signed and the integrity is validated,
 * it will check if one of the certificate of each file
 * is in one of the keystore.
 *
 */

public class JarVerifier {

	/**
	 * Set of certificates of the JAR file
	 */
	private Collection /* of List of Certificate */
	certificateEntries;

	/**
	 * List of certificates of the KeyStores
	 */
	private List /* of List of KeystoreHandle */
	listOfKeystoreHandles;

	/**
	 * check validity of keystore
	 * default == FALSE 
	 */
	private boolean shouldVerifyKeystore = false;

	/**
	 * Retrieve all keystore handles (reload keystores)
	 * default == FALSE 
	 */
	private boolean shouldRetrieveKeystoreHandles = false;

	/**
	 * Number of files in the JarFile
	 */
	private int entries;

	/**
	 * ProgressMonitor during integrity validation
	 */
	private IProgressMonitor monitor;

	/**
	 * JAR File Name: used in the readJarFile.
	 */
	private String jarFileName;

	/**
	 * ResultCode
	 */
	private int resultCode;

	/**
	 * Result Error
	 */
	private Exception resultException;

	//RESULT VALUES
	public static final int NOT_SIGNED = 0;
	public static final int CORRUPTED = 1;
	public static final int INTEGRITY_VERIFIED = 2;
	public static final int SOURCE_VERIFIED = 3;
	public static final int UNKNOWN_ERROR = 4;
	public static final int VERIFICATION_CANCELLED = 5;

	/**
	 * Default Constructor
	 */
	public JarVerifier() {
	}
	/**
	 * 
	 */
	public JarVerifier(IProgressMonitor monitor) {
		this.monitor = monitor;
	}
	/**
	 * Returns the list of certificates of the keystore.
	 *
	 * Can be optimize, within an operation, we only need to get the
	 * list of certificate once.
	 */
	private List getKeyStores() throws CoreException {
		if (listOfKeystoreHandles == null || shouldRetrieveKeystoreHandles) {
			listOfKeystoreHandles = new ArrayList(0);
			KeyStores listOfKeystores = new KeyStores();
			InputStream in = null;
			KeyStore keystore = null;
			while (listOfKeystores.hasNext()) {
				try {
					KeystoreHandle handle = listOfKeystores.next();
					keystore = KeyStore.getInstance(handle.getType());
					in = handle.getLocation().openStream();
					keystore.load(in, null); // no password
				} catch (NoSuchAlgorithmException e) {
					throw newCoreException("Unable to find encrption algorithm", e);

				} catch (CertificateException e) {
					throw newCoreException("Unable to load a certificate in the keystore", e);
				} catch (IOException e) {
					// open error message, the keystore is not valid
					throw newCoreException("Unable to access keystore", e);
				} catch (KeyStoreException e) {
					throw newCoreException("Unable to find provider for the keystore type", e);
				} finally {
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
						} // nothing
					}
				} // try loading a keyStore

				// keystore was loaded
				listOfKeystoreHandles.add(keystore);

			} // while all key stores

		}

		return listOfKeystoreHandles;
	}
	/**
	 */
	public Exception getResultException() {
		return resultException;
	}
	/**
	 * initialize instance variables
	 */
	private void initializeVariables(File jarFile) throws IOException {
		resultCode = UNKNOWN_ERROR;
		resultException = new Exception(Policy.bind("JarVerifier.InvalidJarFile", jarFile.getAbsolutePath())); //$NON-NLS-1$
		JarFile jar = new JarFile(jarFile);
		entries = jar.size();
		try {
			jar.close();
		} catch (java.io.IOException ex) {
			// unchecked
		}
		jarFileName = jarFile.getName();
		certificateEntries = new HashSet();
	}
	/**
	 * Returns true if one of the certificate exists in the keystore
	 */
	private boolean existsInKeystore(Collection certs) throws CoreException {
		try {
			Iterator listOfCerts = certs.iterator();
			while (listOfCerts.hasNext()) {
				List keyStores = getKeyStores();
				if (!keyStores.isEmpty()) {
					Iterator listOfKeystores = keyStores.iterator();
					while (listOfKeystores.hasNext()) {
						KeyStore keystore = (KeyStore) listOfKeystores.next();
						Certificate cert = (Certificate) listOfCerts.next();
						if (keystore.getCertificateAlias(cert) != null)
							return true;
					}
				}
			}
		} catch (KeyStoreException e) {
			throw newCoreException("KeyStore not loaded", e);
		}
		return false;
	}
	/**
	 * Throws exception or set the resultcode to UNKNOWN_ERROR
	 */
	private List readJarFile(final JarInputStream jis) throws IOException, InterruptedException, InvocationTargetException {
		final List list = new ArrayList(0);

		byte[] buffer = new byte[4096];
		JarEntry ent;
		if (monitor != null)
			monitor.beginTask(Policy.bind("JarVerifier.Verify", jarFileName), entries); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			while ((ent = jis.getNextJarEntry()) != null) {
				list.add(ent);
				if (monitor != null)
					monitor.worked(1);
				while ((jis.read(buffer, 0, buffer.length)) != -1) {
					// Security error thrown if tempered
				}
			}
		} catch (IOException e) {
			resultCode = UNKNOWN_ERROR;
			resultException = e;
		} finally {
			if (monitor != null)
				monitor.done();
		}
		return list;
	}
	/**
	 * 
	 * @param newMonitor org.eclipse.core.runtime.IProgressMonitor
	 */
	public void setMonitor(IProgressMonitor newMonitor) {
		monitor = newMonitor;
	}
	/**
	 * 
	 */
	public void shouldRetrieveHandles(boolean value) {
		shouldRetrieveKeystoreHandles = value;
	}
	/**
	 * 
	 */
	public void shouldVerifyKeystore(boolean value) {
		shouldVerifyKeystore = value;
	}
	/**
	 * Verifies integrity and the validity of a valid
	 * URL representing a JAR file
	 * the possible results are:
	 *
	 * result == NOT_SIGNED	 				if the jar file is not signed.
	 * result == INTEGRITY_VERIFIED		 	if the Jar file has not been
	 *										modified since it has been
	 *										signed
	 * result == CORRUPTED 					if the Jar file has been changed
	 * 										since it has been signed.
	 * result == SOURCE_VERIFIED	 		if all the files in the Jar
	 *										have a certificate that is
	 * 										present in the keystore
	 * result == UNKNOWN_ERROR		 		an occured during process, do
	 *                                      not install.
	 * result == VERIFICATION.CANCELLED     if process was cancelled, do
	 *										not install.
	 * @return int
	 */
	public int verify(File jarFile) {

		try {
			// new verification, clean instance variables
			initializeVariables(jarFile);

			// verify integrity
			verifyIntegrity(jarFile);

			// do not close input stream
			// as verifyIntegrity already did it

			// verify source certificate
			if (resultCode == INTEGRITY_VERIFIED)
				verifyAuthentication();

		} catch (Exception e) {
			resultCode = UNKNOWN_ERROR;
			resultException = e;
		}

		return resultCode;
	}
	/**
	 * Verifies that each file has at least one certificate
	 * valid in the keystore
	 *
	 * At least one certificate from each Certificate Array
	 * of the Jar file must be found in the known Certificates
	 */
	private void verifyAuthentication() throws CoreException {

		Iterator entries = certificateEntries.iterator();
		boolean certificateFound = false;

		// If all the cartificate of an entry are
		// not found in the list of known certifcate
		// we exit the loop.
		while (entries.hasNext() && !certificateFound) {
			List certs = (List) entries.next();
			certificateFound = existsInKeystore(certs);
		}

		if (certificateFound)
			resultCode = SOURCE_VERIFIED;
	}
	/**
	 * Verifies the integrity of the JAR
	 */
	private void verifyIntegrity(File jarFile) {

		JarInputStream jis = null;

		try {
			// If the JAR is signed and not valid
			// a security exception will be thrown
			// while reading it
			jis = new JarInputStream(new FileInputStream(jarFile), true);
			List filesInJar = readJarFile(jis);

			// you have to read all the files once
			// before getting the certificates 
			if (jis.getManifest() != null) {
				Iterator iter = filesInJar.iterator();
				boolean certificateFound = false;
				while (iter.hasNext()) {
					Certificate[] certs = ((JarEntry) iter.next()).getCertificates();
					if ((certs != null) && (certs.length != 0)) {
						certificateFound = true;
						certificateEntries.add(Arrays.asList(certs));
					};
				}

				if (certificateFound)
					resultCode = INTEGRITY_VERIFIED;
				else
					resultCode = NOT_SIGNED;
			}
		} catch (SecurityException e) {
			// Jar file is signed
			// but content has changed since signed
			resultCode = CORRUPTED;
		} catch (InterruptedException e) {
			resultCode = VERIFICATION_CANCELLED;
		} catch (Exception e) {
			resultCode = UNKNOWN_ERROR;
			resultException = e;
		} finally {
			if (jis != null) {
				try {
					jis.close();
				} catch (IOException e) {
				} // nothing
			}
		}

	}
	/**
	 */
	private boolean verifyIntegrityOfKeyStore() {
		return shouldVerifyKeystore;
	}

	/**
	 * 
	 */
	public void installCertificates() {
		Iterator entries = certificateEntries.iterator();
		// each item in the iterator is a List of certificates
		// a JAR can be signed using different certificates,
		// and each Certificate can be a chained certificate
		//  which one do we install ?

		
	}
	
	/**
	 * Gets the certificateEntries.
	 * @return Returns a Collection
	 */
	public Collection getCertificateEntries() {
		return certificateEntries;
	}

	/**
	 * 
	 */
	private CoreException newCoreException(String s, Throwable e) throws CoreException {
		String id = UpdateManagerPlugin.getPlugin().getDescriptor().getUniqueIdentifier();
		return new CoreException(new Status(IStatus.ERROR, id, 0, s, e));
	}

}