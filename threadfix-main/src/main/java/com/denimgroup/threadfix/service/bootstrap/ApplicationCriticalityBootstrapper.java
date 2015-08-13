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
package com.denimgroup.threadfix.service.bootstrap;

import com.denimgroup.threadfix.data.dao.ApplicationCriticalityDao;
import com.denimgroup.threadfix.data.entities.ApplicationCriticality;
import com.denimgroup.threadfix.logging.SanitizedLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.denimgroup.threadfix.CollectionUtils.list;

/**
 * Created by mcollins on 8/13/15.
 */
@Component
public class ApplicationCriticalityBootstrapper {

    private static final SanitizedLogger LOG = new SanitizedLogger(ApplicationCriticalityBootstrapper.class);

    @Autowired
    ApplicationCriticalityDao applicationCriticalityDao;

    public void bootstrap() {
        for (String level : list("Low", "Medium", "High", "Critical")) {
            ApplicationCriticality existingCriticality =
                    applicationCriticalityDao.retrieveByName(level);

            if (existingCriticality == null) {
                ApplicationCriticality criticality = new ApplicationCriticality();

                criticality.setName(level);

                applicationCriticalityDao.saveOrUpdate(criticality);
            } else {
                LOG.debug("Already had criticality " + level);
            }

        }
    }

}
