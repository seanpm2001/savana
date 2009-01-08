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

import org.codehaus.savana.Version;
import org.tmatesoft.svn.cli.svn.SVNCommand;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class SAVCommand extends SVNCommand {
    private static final Logger _sLog = Logger.getLogger("savana-info");

    protected SAVCommand(String name, String[] aliases) {
        super(name, aliases);
    }

    public abstract void doRun() throws SVNException;

    @Override
    public final void run() throws SVNException {
        try {
            log("Savana version: " + Version.VERSION + " (SVNKit version " + Version.SVNKIT_VERSION + ")");
            log("COMMAND BEGIN: " + getSVNEnvironment().getCommandLineString());
            log("Current directory: " + new File("").getAbsolutePath());

            doRun();

            log("COMMAND FINISHED: " + getSVNEnvironment().getCommandLineString());

        // catch runtime exceptions and errors and rethrown them as SVNException
        } catch (RuntimeException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Internal Savana exception: " + e, e), SVNLogType.CLIENT);
        } catch (Error e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Internal Savana error: " + e, e), SVNLogType.CLIENT);
        }
    }

    @Override
    protected SAVCommandEnvironment getSVNEnvironment() {
        return (SAVCommandEnvironment) getEnvironment();
    }

    @Override
    protected String getResourceBundleName() {
        return "org.codehaus.savana.scripts.commands";
    }

    public void logStart(String message) {
        log("Start: " + message);
    }

    public void logEnd(String message) {
        log("End:   " + message);
    }

    public void log(String message) {
        _sLog.log(Level.FINE, getClass().getSimpleName() + " " + message);
    }
}
