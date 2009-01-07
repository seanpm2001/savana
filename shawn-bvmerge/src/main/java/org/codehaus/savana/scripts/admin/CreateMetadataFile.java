/*
 * Savana - Transactional Workspaces for Subversion
 * Copyright (C) 2006-2009  Bazaarvoice Inc.
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
package org.codehaus.savana.scripts.admin;

import org.codehaus.savana.BranchType;
import org.codehaus.savana.MetadataFile;
import org.codehaus.savana.PathUtil;
import org.codehaus.savana.WorkingCopyInfo;
import org.codehaus.savana.scripts.SAVCommand;
import org.codehaus.savana.scripts.SAVCommandEnvironment;
import org.codehaus.savana.scripts.SAVOption;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CreateMetadataFile extends SAVCommand {

    public CreateMetadataFile() {
        super("createmetadatafile", new String[]{"bootstrap"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new ArrayList();
        options.add(SAVOption.PROJECT_NAME);
        options.add(SAVOption.TRUNK_PATH);
        options.add(SAVOption.RELEASE_BRANCHES_PATH);
        options.add(SAVOption.USER_BRANCHES_PATH);
        return options;
    }

    public void doRun() throws SVNException {
        SAVCommandEnvironment env = getSVNEnvironment();

        //Parse command-line arguments
        List<String> targets = env.combineTargets(null, true);
        if (targets.size() < 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        if (targets.size() > 3) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR), SVNLogType.CLIENT);
        }
        String projectRoot = targets.get(0);
        BranchType branchType = null;
        try {
            branchType = BranchType.fromKeyword(targets.get(1));
        } catch (IllegalArgumentException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, e.getMessage()), SVNLogType.CLIENT);
        }
        String sourceBranchName = (targets.size() > 2) ? targets.get(2) : null;

        //Default the optional arguments if they weren't specified
        String projectName = env.getProjectName();
        if (projectName == null) {
            projectName = SVNPathUtil.tail(projectRoot);
        }

        //Source path is required for release and user branches, not allowed for trunk branch
        if (branchType == BranchType.TRUNK && sourceBranchName != null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS,
                    "ERROR: source branch may not be specified for the trunk branch"), SVNLogType.CLIENT);
        } else if (branchType != BranchType.TRUNK && sourceBranchName == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS,
                    "ERROR: source branch must be specified for release and user branches"), SVNLogType.CLIENT);
        }

        //Make sure the branch paths are all different from each other
        String trunkPath = env.getTrunkPath();
        String releaseBranchesPath = env.getReleaseBranchesPath();
        String userBranchesPath = env.getUserBranchesPath();
        if (trunkPath.equals(releaseBranchesPath) || trunkPath.equals(userBranchesPath) || releaseBranchesPath.equals(userBranchesPath)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR,
                    "ERROR: trunk, release and user branches paths must be different from each other"), SVNLogType.CLIENT);
        }

        //Create the metadata file in the current directory
        File workspaceDir = new File(System.getProperty("user.dir"));
        File metadataFile = new File(workspaceDir, MetadataFile.METADATA_FILE_NAME);

        //Determine the file's path relative to the repository and use it to compute the branch path
        SVNWCClient wcClient = env.getClientManager().getWCClient();
        SVNInfo workspaceDirInfo = wcClient.doInfo(workspaceDir, SVNRevision.WORKING);
        SVNURL repositoryUrl = workspaceDirInfo.getRepositoryRootURL();
        SVNURL workspaceDirUrl = workspaceDirInfo.getURL();
        String workspaceDirName = SVNPathUtil.tail(workspaceDirUrl.getPath());
        String branchPath = getPathWithinRepo(branchType, workspaceDirName, projectRoot, trunkPath, releaseBranchesPath, userBranchesPath);

        //Make sure the workspaceDirUrl and the branch path match
        String workspaceDirPath = PathUtil.getPathTail(workspaceDirUrl.getPath(), repositoryUrl.getPath());
        if (!workspaceDirPath.equalsIgnoreCase(branchPath)) {
            String errorMessage =
                    "ERROR: Branch type argument does not match current repository location." +
                    "\nExpected Branch Path: " + branchPath +
                    "\nCurrent Repository Location: " + workspaceDirPath;
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.BAD_RELATIVE_PATH, errorMessage), SVNLogType.CLIENT);
        }

        //Calculate values that are only relevant to release and user branches.
        String branchPointRevision = null;
        String sourcePath = null;
        if (branchType != BranchType.TRUNK) {
            //Calculate the source path from the source branch name
            BranchType sourceBranchType = BranchType.TRUNK.getKeyword().equalsIgnoreCase(sourceBranchName) ?
                    BranchType.TRUNK : BranchType.RELEASE_BRANCH;
            sourcePath = getPathWithinRepo(sourceBranchType, sourceBranchName, projectRoot, trunkPath, releaseBranchesPath, userBranchesPath);

            //Make sure the source path exists in the repository already
            SVNRepository repository = env.getClientManager().createRepository(repositoryUrl, true);
            if (repository.checkPath(repository.getRepositoryPath(sourcePath), -1) != SVNNodeKind.DIR) {
                String errorMessage =
                        "ERROR: Could not find source branch." +
                        "\nSource Branch Path: " + sourcePath;
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, errorMessage), SVNLogType.CLIENT);
            }

            //Assume the branch was created at the revision of the first log entry for this directory.
            RevisionNumberLogEntryHandler logEntryHandler = new RevisionNumberLogEntryHandler();
            if (repository.log(new String[]{branchPath}, 1, -1, false, true, 1, logEntryHandler) > 0) {
                branchPointRevision = Long.toString(logEntryHandler.getRevision() - 1);
            } else {
                branchPointRevision = Long.toString(repository.getLatestRevision()); // should never happen happen
            }
        }

        try {
            //Try to create the file
            if (!metadataFile.createNewFile()) {
                String errorMessage =
                        "ERROR: could not create metadata file." +
                                "\nPath: " + metadataFile.getAbsolutePath();
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, errorMessage), SVNLogType.CLIENT);
            }

            //Write some text into the file to warn users not to modify it
            FileWriter writer = new FileWriter(metadataFile, false);
            writer.write("DO NOT MODIFY THIS FILE\n");
            writer.write("This file is used by Savana (sav) to store metadata about branches\n");
            writer.close();

        } catch (IOException ioe) {
            String errorMessage =
                    "ERROR: could not write to metadata file: " + ioe +
                            "\nPath: " + metadataFile.getAbsolutePath();
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, errorMessage), SVNLogType.CLIENT);
        }

        //Add the file to source control
        wcClient.doAdd(metadataFile, false, false, false, SVNDepth.EMPTY, false, false);

        //Set the properties on the file
        wcClient.doSetProperty(metadataFile, MetadataFile.PROP_PROJECT_NAME, SVNPropertyValue.create(projectName), false, SVNDepth.EMPTY, null, null);
        wcClient.doSetProperty(metadataFile, MetadataFile.PROP_PROJECT_ROOT, SVNPropertyValue.create(projectRoot), false, SVNDepth.EMPTY, null, null);
        wcClient.doSetProperty(metadataFile, MetadataFile.PROP_BRANCH_TYPE, SVNPropertyValue.create(branchType.getKeyword()), false, SVNDepth.EMPTY, null, null);
        wcClient.doSetProperty(metadataFile, MetadataFile.PROP_BRANCH_PATH, SVNPropertyValue.create(branchPath), false, SVNDepth.EMPTY, null, null);
        wcClient.doSetProperty(metadataFile, MetadataFile.PROP_TRUNK_PATH, SVNPropertyValue.create(trunkPath), false, SVNDepth.EMPTY, null, null);
        wcClient.doSetProperty(metadataFile, MetadataFile.PROP_RELEASE_BRANCHES_PATH, SVNPropertyValue.create(releaseBranchesPath), false, SVNDepth.EMPTY, null, null);
        wcClient.doSetProperty(metadataFile, MetadataFile.PROP_USER_BRANCHES_PATH, SVNPropertyValue.create(userBranchesPath), false, SVNDepth.EMPTY, null, null);

        //If we aren't on the trunk...
        if (branchType != BranchType.TRUNK) {
            wcClient.doSetProperty(metadataFile, MetadataFile.PROP_SOURCE_PATH, SVNPropertyValue.create(sourcePath), false, SVNDepth.EMPTY, null, null);
            wcClient.doSetProperty(metadataFile, MetadataFile.PROP_BRANCH_POINT_REVISION, SVNPropertyValue.create(branchPointRevision), false, SVNDepth.EMPTY, null, null);
            wcClient.doSetProperty(metadataFile, MetadataFile.PROP_LAST_MERGE_REVISION, SVNPropertyValue.create(branchPointRevision), false, SVNDepth.EMPTY, null, null);
        }

        env.getOut().println("-------------------------------------------------");
        env.getOut().println("SUCCESS: Created metadata file.");
        env.getOut().println("-------------------------------------------------");
        env.getOut().println();
        WorkingCopyInfo wcInfo = new WorkingCopyInfo(env.getClientManager());
        env.getOut().println(wcInfo);
        env.getOut().println();
        env.getOut().println("Please 'svn commit' to save the metadata file to the Subversion repository:\n  " + metadataFile);
    }

    private String getPathWithinRepo(BranchType branchType, String branchName, String projectRoot,
                                     String trunkPath, String releaseBranchesPath, String userBranchesPath) {
        String branchTypePath;
        switch (branchType) {
            case TRUNK:
                branchTypePath = trunkPath;
                break;
            case RELEASE_BRANCH:
                branchTypePath = releaseBranchesPath;
                break;
            case USER_BRANCH:
                branchTypePath = userBranchesPath;
                break;
            default:
                throw new UnsupportedOperationException("Unknown branch type: " + branchType);
        }
        String path = SVNPathUtil.append(projectRoot, branchTypePath);

        if (branchType != BranchType.TRUNK) {
            path = SVNPathUtil.append(path, branchName);
        }

        return path;
    }

    private static class RevisionNumberLogEntryHandler implements ISVNLogEntryHandler {
        private long _revision = -1;

        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            _revision = logEntry.getRevision();
        }

        public long getRevision() {
            return _revision;
        }
    }
}