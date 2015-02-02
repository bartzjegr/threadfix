////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2015 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.sonarplugin;

import com.denimgroup.threadfix.data.entities.VulnerabilityMarker;
import com.denimgroup.threadfix.data.interfaces.Endpoint;
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabase;
import com.denimgroup.threadfix.framework.engine.full.EndpointDatabaseFactory;
import com.denimgroup.threadfix.remote.PluginClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.SonarIndex;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.rule.RuleKey;

import java.util.Collection;
import java.util.Map;

import static com.denimgroup.threadfix.sonarplugin.ThreadFixCWERulesDefinition.REPOSITORY_KEY;

/**
 * Created by mcollins on 1/28/15.
 */
public class ThreadFixSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadFixSensor.class);

    private ThreadFixInfo info = null;
    private final ResourcePerspectives resourcePerspectives;
    private SonarIndex sonarIndex;

    public ThreadFixSensor(ResourcePerspectives resourcePerspectives,
                           Settings settings,
                           FileSystem moduleFileSystem,
                           SonarIndex sonarIndex) {
        this.sonarIndex = sonarIndex;
        checkProperties(settings);
        runHAM(moduleFileSystem);

        if (resourcePerspectives == null) {
            LOG.error("Got null resources perspective from autowiring. Will probably die.");
        }
        this.resourcePerspectives = resourcePerspectives;
    }

    private void runHAM(FileSystem moduleFileSystem) {

        EndpointDatabase database = EndpointDatabaseFactory.getDatabase(moduleFileSystem.baseDir());

        if (database != null) {
            LOG.info("Got an EndpointDatabase successfully:");
            for (Endpoint endpoint : database) {
                LOG.info(endpoint.toString());
            }
        } else {
            LOG.info("Failed to get an EndpointDatabase.");
        }
    }

    private void checkProperties(Settings settings) {
        Map<String, String> properties = settings.getProperties();

        LOG.info("Starting ThreadFix configuration check.");

        ThreadFixInfo info = new ThreadFixInfo(properties);

        if (!info.valid()) {
            LOG.info("Invalid ThreadFix configuration.");
            for (String error : info.getErrors()) {
                LOG.info(error);
            }
            this.info = null;
        } else if (testConnection(info)) {
            LOG.info("ThreadFix connection was valid.");
            this.info = info;
        } else {
            LOG.info("ThreadFix properties were present but the connection failed.");
            this.info = null;
        }
    }

    private boolean testConnection(ThreadFixInfo info) {
        return info.getApplicationId() != null;
    }

    @Override
    public void analyse(Project project, SensorContext sensorContext) {

        if (info != null) {
            PluginClient client = getConfiguredClient();

            VulnerabilityMarker[] endpoints = client.getVulnerabilityMarkers(info.getApplicationId());

            for (VulnerabilityMarker vulnerabilityMarker : endpoints) {
                LOG.debug("Got endpoint " + vulnerabilityMarker);

                processMarker(vulnerabilityMarker, sensorContext);
            }

            LOG.info("Setting total vulns to " + endpoints.length);

            String data = String.valueOf(endpoints.length);
            Double aDouble = Double.valueOf(data);

            Measure measure1 = new Measure(ThreadFixMetrics.TOTAL_VULNS, aDouble);
            measure1.setValue(aDouble);
            measure1.setPersistenceMode(PersistenceMode.FULL);
            sensorContext.saveMeasure(measure1);
        }
    }

    private void processMarker(VulnerabilityMarker vulnerabilityMarker, SensorContext sensorContext) {

        Resource resource = resourceOf(sensorContext, vulnerabilityMarker);

        addIssue(resource, vulnerabilityMarker);
    }

    private void addIssue(Resource resource, VulnerabilityMarker vulnerability) {
        if (resource != null) {

            LOG.debug("Got a resource properly.");

            Issuable issuable = this.resourcePerspectives.as(Issuable.class, resource);
            if(issuable != null) {

                RuleKey key = RuleKey.of(REPOSITORY_KEY, "cwe-" + vulnerability.getGenericVulnId());

                String lineNumber = vulnerability.getLineNumber();
                lineNumber = "-1".equals(lineNumber) || "0".equals(lineNumber) ? "1" : lineNumber;
                Issue issue = issuable
                        .newIssueBuilder()
                        .ruleKey(key)
                        .line(Integer.valueOf(lineNumber))
                        .message(vulnerability.getGenericVulnName()).build();

                if (issuable.addIssue(issue)) {
                    LOG.debug("Successfully added issue " + issue);
                } else {
                    LOG.debug("Failed to add issue " + issue);
                }
            }
        } else {
            LOG.debug("Got null resource for path " + vulnerability.getFilePath());
        }
    }

    private Resource resourceOf(SensorContext context, final VulnerabilityMarker vulnerability) {

        vulnerability.getFilePath();

        Resource resource = searchAllResources(vulnerability.getFilePath());

        if (context.getResource(resource) != null) {
            return resource;
        } else {
            LOG.debug("File \"{}\" is not indexed. Skip it.", vulnerability.getFilePath());
            return null;
        }
    }

    private Resource searchAllResources(final String componentKey) {
        if (componentKey == null || "".equals(componentKey)) {
            LOG.debug("Empty marker passed to searchAllResources.");
            return null;
        }

        final Collection<Resource> resources = this.sonarIndex.getResources();

        for (final Resource resource : resources) {
            if (resource.getKey().endsWith(componentKey) || componentKey.endsWith(resource.getKey())) {
                LOG.debug("Found resource for [" + componentKey + "]");
                LOG.debug("Resource class type: [" + resource.getClass().getName() + "]");
                LOG.debug("Resource key: [" + resource.getKey() + "]");
                LOG.debug("Resource id: [" + resource.getId() + "]");
                return resource;
            } else {
                LOG.debug("no match for " + resource.getKey());
            }
        }

        LOG.debug("No resource found for component [" + componentKey + "]");
        return null;
    }

    public PluginClient getConfiguredClient() {
        return new PluginClient(info.getUrl(), info.getApiKey());
    }

    @Override
    public boolean shouldExecuteOnProject(Project project) {
        return true;
    }
}
