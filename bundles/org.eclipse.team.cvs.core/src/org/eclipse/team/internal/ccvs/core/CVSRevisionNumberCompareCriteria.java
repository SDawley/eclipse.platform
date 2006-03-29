/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.core;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.core.variants.IResourceVariantComparator;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;

/**
 * CVSRevisionNumberCompareCriteria
 */
 public class CVSRevisionNumberCompareCriteria implements IResourceVariantComparator {
 	
	private boolean isThreeWay;

	public CVSRevisionNumberCompareCriteria(boolean isThreeWay) {
		this.isThreeWay = isThreeWay;
	}
	
	/**
	 * @see RemoteSyncElement#timestampEquals(IResourceVariant, IResourceVariant)
	 */
	protected boolean compare(ICVSRemoteResource e1, ICVSRemoteResource e2) {
		if(e1.isContainer()) {
			if(e2.isContainer()) {
				return true;
			}
			return false;
		}
		return e1.equals(e2);
	}

	/**
	 * @see RemoteSyncElement#timestampEquals(IResource, IResourceVariant)
	 */
	protected boolean compare(IResource e1, ICVSRemoteResource e2) {
		if(e1.getType() != IResource.FILE) {
			if(e2.isContainer()) {
				return true;
			}
			return false;
		}
		ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor((IFile)e1);
		try {
			byte[] syncBytes1 = cvsFile.getSyncBytes();
			byte[] syncBytes2 = ((ICVSRemoteFile)e2).getSyncBytes();
		
			if(syncBytes1 != null) {
				if(ResourceSyncInfo.isDeletion(syncBytes1) || ResourceSyncInfo.isMerge(syncBytes1) || cvsFile.isModified(null)) {
					return false;
				}
				return ResourceSyncInfo.getRevision(syncBytes1).equals(ResourceSyncInfo.getRevision(syncBytes2));
			}
			return false;
		} catch(CVSException e) {
			CVSProviderPlugin.log(e);
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.IComparisonCriteria#compare(org.eclipse.core.resources.IResource, org.eclipse.team.core.subscribers.ISubscriberResource)
	 */
	public boolean compare(IResource local, IResourceVariant remote) {
		return compare(local, (ICVSRemoteResource)remote);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.IComparisonCriteria#compare(org.eclipse.team.core.subscribers.ISubscriberResource, org.eclipse.team.core.subscribers.ISubscriberResource)
	 */
	public boolean compare(IResourceVariant base, IResourceVariant remote) {
		return compare((ICVSRemoteResource)base, (ICVSRemoteResource)remote);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.ISubscriberResourceComparator#isThreeWay()
	 */
	public boolean isThreeWay() {
		return isThreeWay;
	}
}
