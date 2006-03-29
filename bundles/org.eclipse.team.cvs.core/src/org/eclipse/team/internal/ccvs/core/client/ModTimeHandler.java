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
package org.eclipse.team.internal.ccvs.core.client;


import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSMessages;
import org.eclipse.team.internal.ccvs.core.util.CVSDateFormatter;

/**
 * Handles a "Mod-time" response from the CVS server.
 * <p>
 * Suppose as a result of performing a command the CVS server responds
 * as follows:<br>
 * <pre>
 *   [...]
 *   Mod-time 18 Oct 2001 20:21:13 -0330\n
 *   [...]
 * </pre>
 * Then we parse and remember the date for use in subsequent
 * file transfer responses such as Updated.
 * </p>
 */
class ModTimeHandler extends ResponseHandler {
	public String getResponseID() {
		return "Mod-time"; //$NON-NLS-1$
	}

	public void handle(Session session, String timeStamp,
		IProgressMonitor monitor) throws CVSException {
		try {
			session.setModTime(CVSDateFormatter.serverStampToDate(timeStamp));
		} catch (ParseException e) {
			throw new CVSException(NLS.bind(CVSMessages.ModTimeHandler_invalidFormat, new String[] { timeStamp }), e); 
		}
	}
}

