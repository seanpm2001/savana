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
package org.codehaus.savana;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

public class MetadataProperties {

    /**
     * User-friendly name of the project.  For example: 'myproject'.
     */
    private String _projectName;

    /**
     * Path within the repository of the top-level directory of the project.  This defaults
     * to the name of the project.  For example: 'myproject'.  Generally this directory has
     * children organized as follows:
     * <pre>
     *   myproject/                - project root directory
     *   myproject/trunk/          - the project trunk branch
     *   myproject/branches/       - the parent directory of the release branches
     *   myproject/branches/user/  - the parent directory of the user branches
     *   myproject/tags/           - read-only branches corresponding to specific releases [optional]
     * </pre>
     */
    private String _projectRoot;

    /**
     * The type of branch: trunk, release branch, user branch.
     */
    private BranchType _branchType;

    /**
     * Path within the repository where this branch lives (eg. 'myproject/branches/user/jdoe-1234').
     */
    private String _branchPath;

    /**
     * Path within the repository from which this branch was derived (eg. 'myproject/trunk').  This will
     * be null for the 'trunk' branch.
     */
    private String _sourcePath;

    /**
     * Path underneath the project root of the project trunk branch.
     * For example: 'trunk'.
     */
    private String _trunkPath = BranchType.TRUNK.getDefaultPath();

    /**
     * Path underneath the project root of the directory containing release branches.
     * For example: 'branches'.
     */
    private String _releaseBranchesPath = BranchType.RELEASE_BRANCH.getDefaultPath();

    /**
     * Path underneath the project root of the directory containing user branches.
     * For example: 'branches/user'.
     */
    private String _userBranchesPath = BranchType.USER_BRANCH.getDefaultPath();

    /**
     * The revision when this branch was created.  Null for the 'trunk' branch.
     */
    private SVNRevision _branchPointRevision;

    /**
     * The revision when changes in the source path were last pulled down into this branch
     * (using the 'synchronize' operation).  Null for the 'trunk' branch.
     */
    private SVNRevision _lastMergeRevision;

    private ISavanaPolicies _savanaPolicies;

    /**
     * Creates a MetadataProperties from a file in a remote repository.
     */
    public MetadataProperties(SVNRepository repository, String metadataFilePath, long revision) throws SVNException {
        SVNProperties properties = new SVNProperties();
        // note: SVNRepository.getFile() is *much* faster than SVNWCClient.doGetProperty(SVNURL...).  ListBranches is 2x faster using getFile().
        repository.getFile(metadataFilePath, revision, properties, null);
        init(properties);
    }

    /**
     * Creates a MetadataProperties from a metadata file on the local file system.
     */
    public MetadataProperties(SVNClientManager clientManager, File metadataFile) throws SVNException {
        SVNProperties properties = new SVNProperties();
        SVNWCClient wcClient = clientManager.getWCClient();
        wcClient.doGetProperty(metadataFile, null, SVNRevision.WORKING, SVNRevision.WORKING, SVNDepth.EMPTY, new PropertyHandler(properties), null);
        init(properties);
    }

    private void init(SVNProperties properties) throws SVNException {
        SVNPropertyValue projectNameProps = properties.getSVNPropertyValue(MetadataFile.PROP_PROJECT_NAME);
        if (projectNameProps != null) {
            _projectName = _projectRoot = SVNPropertyValue.getPropertyAsString(projectNameProps);
        }

        SVNPropertyValue projectRootProps = properties.getSVNPropertyValue(MetadataFile.PROP_PROJECT_ROOT);
        if (projectRootProps != null) {
            _projectRoot = SVNPropertyValue.getPropertyAsString(projectRootProps);
        }

        SVNPropertyValue branchTypeProps = properties.getSVNPropertyValue(MetadataFile.PROP_BRANCH_TYPE);
        if (branchTypeProps != null) {
            _branchType = BranchType.fromKeyword(SVNPropertyValue.getPropertyAsString(branchTypeProps));
        }

        SVNPropertyValue branchPathProps = properties.getSVNPropertyValue(MetadataFile.PROP_BRANCH_PATH);
        if (branchPathProps != null) {
            _branchPath = SVNPropertyValue.getPropertyAsString(branchPathProps);
        }

        SVNPropertyValue sourcePathProps = properties.getSVNPropertyValue(MetadataFile.PROP_SOURCE_PATH);
        if (sourcePathProps != null) {
            _sourcePath = SVNPropertyValue.getPropertyAsString(sourcePathProps);
        }

        SVNPropertyValue trunkPathProps = properties.getSVNPropertyValue(MetadataFile.PROP_TRUNK_PATH);
        if (trunkPathProps != null) {
            _trunkPath = SVNPropertyValue.getPropertyAsString(trunkPathProps);
        }

        SVNPropertyValue releaseBranchesPathProps = properties.getSVNPropertyValue(MetadataFile.PROP_RELEASE_BRANCHES_PATH);
        if (releaseBranchesPathProps != null) {
            _releaseBranchesPath = SVNPropertyValue.getPropertyAsString(releaseBranchesPathProps);
        }

        SVNPropertyValue userBranchesPathProps = properties.getSVNPropertyValue(MetadataFile.PROP_USER_BRANCHES_PATH);
        if (userBranchesPathProps != null) {
            _userBranchesPath = SVNPropertyValue.getPropertyAsString(userBranchesPathProps);
        }

        SVNPropertyValue branchPointRevisionProps = properties.getSVNPropertyValue(MetadataFile.PROP_BRANCH_POINT_REVISION);
        if (branchPointRevisionProps != null) {
            _branchPointRevision = SVNRevision.create(Long.parseLong(SVNPropertyValue.getPropertyAsString(branchPointRevisionProps)));
        }

        SVNPropertyValue lastMergeRevisionProps = properties.getSVNPropertyValue(MetadataFile.PROP_LAST_MERGE_REVISION);
        if (lastMergeRevisionProps != null) {
            _lastMergeRevision = SVNRevision.create(Long.parseLong(SVNPropertyValue.getPropertyAsString(lastMergeRevisionProps)));
        }

        SVNPropertyValue savanaPoliciesProps = properties.getSVNPropertyValue(MetadataFile.PROP_SAVANA_POLICIES);
        if (savanaPoliciesProps != null) {
            _savanaPolicies = parsePolicies(SVNPropertyValue.getPropertyAsString(savanaPoliciesProps));
        }
    }

    private ISavanaPolicies parsePolicies(String policiesString) throws SVNException {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(policiesString));
        } catch (Exception e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.BAD_PROPERTY_VALUE,
                    "Error parsing Savana policy properties: " + e), SVNLogType.CLIENT);
        }
        String className = properties.getProperty("class");
        if (className == null) {
            return null;
        }
        ISavanaPolicies savanaPolicies = null;
        try {
            savanaPolicies = (ISavanaPolicies) Class.forName(className).newInstance();
        } catch(Exception e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.BAD_PROPERTY_VALUE,
                    "Error creating Savana policy class instance: " + className, e), SVNLogType.CLIENT);
        }
        savanaPolicies.initialize(properties);
        return savanaPolicies;
    }

    public String getProjectName() {
        return _projectName;
    }

    public String getProjectRoot() {
        return _projectRoot;
    }

    public BranchType getBranchType() {
        return _branchType;
    }

    public String getBranchPath() {
        return _branchPath;
    }

    public String getBranchName() {
        return (_branchPath != null) ? SVNPathUtil.tail(_branchPath) : null;
    }

    public String getSourcePath() {
        return _sourcePath;
    }

    public String getSourceName() {
        return (_sourcePath != null) ? SVNPathUtil.tail(_sourcePath) : null;
    }

    public BranchType getSourceBranchType() {
        if (_sourcePath == null) {
            return null;
        } else if (_sourcePath.equals(_trunkPath)) {
            return BranchType.TRUNK;
        } else if (SVNPathUtil.removeTail(_sourcePath).equals(_releaseBranchesPath)) {
            return BranchType.RELEASE_BRANCH;
        } else {
            return BranchType.USER_BRANCH;
        }
    }

    public String getBranchTreeRootPath() {
        //Return the path that is the source of all branches from this point.
        // * For a user branch, that's just the real source of the user branch.
        // * Since release branches are like a trunk for user branches made off of them, we want to return the path of the release branch.
        // * For the trunk, there is no source path, so we want to return the branch path (which is trunk)
        return _branchType == BranchType.USER_BRANCH ? _sourcePath : _branchPath;
    }

    public String getTrunkPath() {
        return SVNPathUtil.append(getProjectRoot(), _trunkPath);
    }

    public String getReleaseBranchPath(String branchName) {
        return SVNPathUtil.append(SVNPathUtil.append(getProjectRoot(), _releaseBranchesPath), branchName);
    }

    public String getUserBranchPath(String branchName) {
        return SVNPathUtil.append(SVNPathUtil.append(getProjectRoot(), _userBranchesPath), branchName);
    }

    public SVNRevision getBranchPointRevision() {
        return _branchPointRevision;
    }

    public SVNRevision getLastMergeRevision() {
        return _lastMergeRevision;
    }

    public ISavanaPolicies getSavanaPolicies() {
        return _savanaPolicies;
    }

    public String toString() {
        StringWriter buf = new StringWriter();
        PrintWriter out = new PrintWriter(buf);
        out.println("---------------------------------------------");
        out.println("Branch Name:           " + getBranchName());
        out.println("---------------------------------------------");
        out.println("Project Name:          " + _projectName);
        out.println("Branch Type:           " + _branchType.getKeyword().toLowerCase());
        out.println("Source:                " + ((getSourceName() != null) ? getSourceName() : "none"));
        out.println("Branch Point Revision: " + ((_branchPointRevision != null) ? _branchPointRevision : "none"));
        out.println("Last Merge Revision:   " + ((_lastMergeRevision != null) ? _lastMergeRevision : "none"));
        return buf.toString().trim();
    }

    private static class PropertyHandler implements ISVNPropertyHandler {
        private final SVNProperties _properties;

        public PropertyHandler(SVNProperties properties) {
            _properties = properties;
        }

        public void handleProperty(File file, SVNPropertyData property) throws SVNException {
            _properties.put(property.getName(), property.getValue());
        }

        public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
            _properties.put(property.getName(), property.getValue());
        }

        public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
            // ignore revision properties
        }
    }
}
