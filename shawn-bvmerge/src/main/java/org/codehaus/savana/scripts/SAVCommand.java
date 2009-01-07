/*
 * Savana - Transactional Workspaces for Subversion
 * Copyright (C) 2009  Bazaarvoice Inc.
 * <p/>
 * This file is part of Savana.
 * <p/>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * Third party components of this software are provided or made available only subject
 * to their respective licenses. The relevant components and corresponding
 * licenses are listed in the "licenses" directory in this distribution. In any event,
 * the disclaimer of warranty and limitation of liability provision in this Agreement
 * will apply to all Software in this distribution.
 *
 * @author Brian Showers (brian@bazaarvoice.com)
 * @author Bryon Jacob (bryon@jacob.net)
 * @author Shawn Smith (shawn@bazaarvoice.com)
 */
package org.codehaus.savana.scripts;

import org.tmatesoft.svn.cli.svn.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class SAVCommand extends SVNCommand {
    private static final Logger _sLog = Logger.getLogger(SAVCommand.class.getName());

    protected SAVCommand(String name, String[] aliases) {
        super(name, aliases);
    }

    public abstract void doRun() throws SVNException;

    public void run() throws SVNException {
        try {
            doRun();

        // catch runtime exceptions and errors and rethrown them as SVNException
        } catch (RuntimeException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, e), SVNLogType.CLIENT);
        } catch (Error e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, e), SVNLogType.CLIENT);
        }
    }

    protected SAVCommandEnvironment getSVNEnvironment() {
        return (SAVCommandEnvironment) getEnvironment();
    }

    protected String getResourceBundleName() {
        return "org.codehaus.savana.scripts.commands";
    }

    public void logStart(String message) {
        _sLog.log(Level.FINE, "Start: " + message);
    }

    public void logEnd(String message) {
        _sLog.log(Level.FINE, "End: " + message);
    }
}